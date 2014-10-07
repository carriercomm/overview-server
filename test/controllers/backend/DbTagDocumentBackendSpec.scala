package controllers.backend

import org.overviewproject.models.DocumentTag
import org.overviewproject.models.tables.DocumentTags

class DbTagDocumentBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbTagDocumentBackend

    def findDocumentTag(documentId: Long, tagId: Long) = {
      import org.overviewproject.database.Slick.simple._
      DocumentTags.filter(_.documentId === documentId).filter(_.tagId === tagId).firstOption(session)
    }
  }

  "DbTagDocumentBackend" should {
    "#createMany" should {
      trait CreateManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val tag = factory.tag(documentSetId=documentSet.id)
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id)
      }

      "tag the requested documents" in new CreateManyScope {
        await(backend.createMany(tag.id, Seq(doc1.id, doc2.id)))
        findDocumentTag(doc1.id, tag.id) must beSome(DocumentTag(doc1.id, tag.id))
        findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
        findDocumentTag(doc3.id, tag.id) must beNone
      }

      "work when double-tagging" in new CreateManyScope {
        await(backend.createMany(tag.id, Seq(doc1.id)))
        await(backend.createMany(tag.id, Seq(doc1.id, doc2.id)))
        findDocumentTag(doc1.id, tag.id) must beSome(DocumentTag(doc1.id, tag.id))
        findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
        findDocumentTag(doc3.id, tag.id) must beNone
      }

      "tag nothing" in new CreateManyScope {
        await(backend.createMany(tag.id, Seq()))
        findDocumentTag(doc1.id, tag.id) must beNone
        findDocumentTag(doc2.id, tag.id) must beNone
        findDocumentTag(doc3.id, tag.id) must beNone
      }
    }

    "#destroyMany" should {
      trait DestroyManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val tag = factory.tag(documentSetId=documentSet.id)
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc2.id, tag.id)
      }

      "untag the requested documents" in new DestroyManyScope {
        factory.documentTag(doc3.id, tag.id)
        await(backend.destroyMany(tag.id, Seq(doc1.id, doc2.id)))
        findDocumentTag(doc1.id, tag.id) must beNone
        findDocumentTag(doc2.id, tag.id) must beNone
        findDocumentTag(doc3.id, tag.id) must beSome(DocumentTag(doc3.id, tag.id))
      }

      "ignore already-untagged documents" in new DestroyManyScope {
        await(backend.destroyMany(tag.id, Seq(doc1.id, doc3.id)))
        findDocumentTag(doc1.id, tag.id) must beNone
        findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
        findDocumentTag(doc3.id, tag.id) must beNone
      }

      "ignore DocumentTags from other tags" in new DestroyManyScope {
        val tag2 = factory.tag(documentSetId=documentSet.id)
        factory.documentTag(doc1.id, tag2.id)
        await(backend.destroyMany(tag.id, Seq(doc1.id)))
        findDocumentTag(doc1.id, tag2.id) must beSome(DocumentTag(doc1.id, tag2.id))
      }

      "untag zero documents" in new DestroyManyScope {
        await(backend.destroyMany(tag.id, Seq()))
        findDocumentTag(doc1.id, tag.id) must beSome(DocumentTag(doc1.id, tag.id))
        findDocumentTag(doc2.id, tag.id) must beSome(DocumentTag(doc2.id, tag.id))
        findDocumentTag(doc3.id, tag.id) must beNone
      }
    }

    "#destroyAll" should {
      trait DestroyAllScope extends BaseScope {
        val documentSet = factory.documentSet()
        val tag = factory.tag(documentSetId=documentSet.id)
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc2.id, tag.id)
      }

      "destroy all document tags" in new DestroyAllScope {
        await(backend.destroyAll(tag.id))
        findDocumentTag(doc1.id, tag.id) must beNone
        findDocumentTag(doc2.id, tag.id) must beNone
        findDocumentTag(doc3.id, tag.id) must beNone
      }

      "ignore DocumentTags from other tags" in new DestroyAllScope {
        val tag2 = factory.tag(documentSetId=documentSet.id)
        factory.documentTag(doc1.id, tag2.id)
        await(backend.destroyAll(tag.id))
        findDocumentTag(doc1.id, tag2.id) must beSome(DocumentTag(doc1.id, tag2.id))
      }
    }
  }
}