package org.overviewproject.util

import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.DocumentSetCreationJobs

class JobUpdaterSpec extends SlickSpecification {

  "JobUpdater" should {
    "update job retry parameters" in new JobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      updater.updateValidJob(jobUpdate)
      
      import org.overviewproject.database.Slick.simple._
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption(session) must beSome(jobUpdate)
    }
    
    "not update cancelled jobs" in new CancelledJobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      updater.updateValidJob(jobUpdate)
      
      import org.overviewproject.database.Slick.simple._
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption(session) must beSome(job)
    }
    
    
    "only update specified job" in new MultipleJobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      updater.updateValidJob(jobUpdate)

      import org.overviewproject.database.Slick.simple._
      val unchangedJob = DocumentSetCreationJobs.filter(_.id === job2.id)
      
      unchangedJob.firstOption(session) must beSome(job2)
    }

  }

  trait JobScope extends DbScope {
    val updater = new TestJobUpdater(session)
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)

    def jobState = InProgress

  }
  
  trait CancelledJobScope extends JobScope {
    override def jobState = Cancelled
  }
  
  trait MultipleJobScope extends JobScope {
    val documentSet2 = factory.documentSet()
    val job2 = factory.documentSetCreationJob(documentSetId = documentSet2.id, treeTitle = Some("another job"), state = InProgress)
  }
  
  class TestJobUpdater(val session: Session) extends JobUpdater with SlickClientInSession
}
