package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorRef

object FileGroupJobQueueProtocol {
  case class CreateDocumentsFromFileGroup(fileGroupId: Long, documentSetId: Long)
  case class FileGroupDocumentsCreated(documentSetId: Long)
}

object FileGroupTaskWorkerProtocol {
  case class RegisterWorker(worker: ActorRef)
  case object TaskAvailable
  case object ReadyForTask
  case class Task(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long)
  case class TaskDone(fileGroupId: Long, uploadedFileId: Long)
}

trait FileGroupJobQueue extends Actor {
  import FileGroupJobQueueProtocol._
  import FileGroupTaskWorkerProtocol._

  protected val storage: Storage

  trait Storage {
    def uploadedFileIds(fileGroupId: Long): Iterable[Long]
  }

  private case class AddTasks(tasks: Iterable[Task])
  private case class JobRequest(requester: ActorRef, documentSetId: Long)

  private val workerPool: mutable.Set[ActorRef] = mutable.Set.empty
  private val taskQueue: mutable.Queue[Task] = mutable.Queue.empty
  private val jobTasks: mutable.Map[Long, Set[Long]] = mutable.Map.empty
  private val jobRequests: mutable.Map[Long, JobRequest] = mutable.Map.empty

  def receive = {
    case RegisterWorker(worker) => {
      workerPool += worker
      if (!taskQueue.isEmpty) worker ! TaskAvailable

    }
    case CreateDocumentsFromFileGroup(fileGroupId, documentSetId) => {
      val fileIds = uploadedFilesInFileGroup(fileGroupId)

      addNewTasksToQueue(documentSetId, fileGroupId, fileIds)
      jobRequests += (fileGroupId -> JobRequest(sender, documentSetId))

      workerPool.map(_ ! TaskAvailable)
    }
    case ReadyForTask => {
      if (!taskQueue.isEmpty) {
        val task = taskQueue.dequeue
        sender ! task
      }
    }
    case TaskDone(fileGroupId: Long, uploadedFileId: Long) => 
      whenTaskIsComplete(fileGroupId, uploadedFileId) {
        notifyRequesterIfJobIsDone
      }

  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId).toSet

  private def addNewTasksToQueue(documentSetId: Long, fileGroupId: Long, uploadedFileIds: Set[Long]): Unit = {
    val newTasks = uploadedFileIds.map(Task(documentSetId, fileGroupId, _))
    taskQueue ++= newTasks
    jobTasks += (fileGroupId -> uploadedFileIds)
  }

  private def whenTaskIsComplete(fileGroupId: Long, uploadedFileId: Long)(f: (JobRequest, Long, Set[Long]) => Unit) =
    for {
      tasks <- jobTasks.get(fileGroupId)
      request <- jobRequests.get(fileGroupId)
      remainingTasks = tasks - uploadedFileId
    } f(request, fileGroupId, remainingTasks)

  private def notifyRequesterIfJobIsDone(request: JobRequest, fileGroupId: Long, remainingTasks: Set[Long]): Unit =
    if (remainingTasks.isEmpty) {
      jobTasks -= fileGroupId
      jobRequests -= fileGroupId

      request.requester ! FileGroupDocumentsCreated(request.documentSetId)
    } else {
      jobTasks += (fileGroupId -> remainingTasks)
    }

}