package test.helpers.factories

import java.sql.{Connection,Timestamp}
import scala.slick.jdbc.UnmanagedSession
import scala.util.Random

import org.overviewproject.models._
import org.overviewproject.models.tables._
import org.overviewproject.tree.orm._
import org.overviewproject.util.DocumentSetVersion

/** Creates objects in the database while returning them.
  *
  * Pass `0L` into any primary key column to have DbFactory select an ID
  * automatically.
  *
  * @see Factory
  */
class DbFactory(connection: Connection) extends Factory {
  private implicit val session = new UnmanagedSession(connection)
  private val podoFactory = PodoFactory

  override def document(
    id: Long = 0L,
    documentSetId: Long = 0L,
    description: String = "",
    title: Option[String] = None,
    suppliedId: Option[String] = None,
    text: Option[String] = None,
    url: Option[String] = None,
    documentcloudId: Option[String] = None,
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    pageNumber: Option[Int] = None
  ) = {
    val document = podoFactory.document(
      id,
      documentSetId,
      description,
      title,
      suppliedId,
      text,
      url,
      documentcloudId,
      fileId,
      pageId,
      pageNumber
    )
    DbFactory.queries.insertDocument += document
    document
  }

  override def documentSet(
    id: Long = 0L,
    title: String = "",
    query: Option[String] = None,
    isPublic: Boolean = false,
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    documentCount: Int = 4,
    documentProcessingErrorCount: Int = 3,
    importOverflowCount: Int = 2,
    uploadedFileId: Option[Long] = None,
    version: Int = DocumentSetVersion.current,
    deleted: Boolean = false
  ) = {
    val documentSet = podoFactory.documentSet(
      id,
      title,
      query,
      isPublic,
      createdAt,
      documentCount,
      documentProcessingErrorCount,
      importOverflowCount,
      uploadedFileId,
      version,
      deleted
    )
    DbFactory.queries.insertDocumentSet += documentSet
    documentSet
  }

  override def searchResult(
    id: Long = 0L,
    state: SearchResultState.Value = SearchResultState.Complete,
    documentSetId: Long = 0L,
    query: String = "query",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)
  ) = {
    val searchResult = podoFactory.searchResult(
      id,
      state,
      documentSetId,
      query,
      createdAt
    )
    DbFactory.queries.insertSearchResult += searchResult
    searchResult
  }

  override def documentSearchResult(
    documentId: Long,
    searchResultId: Long
  ) = {
    val documentSearchResult = podoFactory.documentSearchResult(
      documentId = documentId,
      searchResultId = searchResultId
    )
    DbFactory.queries.insertDocumentSearchResult += documentSearchResult
    documentSearchResult
  }
}

object DbFactory {
  object queries {
    import org.overviewproject.database.Slick.simple._

    // Compile queries once, instead of once per test
    val insertDocument = (Documents returning Documents).insertInvoker
    val insertDocumentSet = (DocumentSets returning DocumentSets).insertInvoker
    val insertSearchResult = (SearchResults returning SearchResults).insertInvoker
    val insertDocumentSearchResult = (DocumentSearchResults returning DocumentSearchResults).insertInvoker
  }
}