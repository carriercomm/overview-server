package clustering 

import models._
import ClusterTypes._

import scala.collection.JavaConversions._
import com.codahale.jerkson.Json._
import com.ning.http.client._
import akka.dispatch.{ExecutionContext,Future,Promise}

// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.) 
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:clustering.DCDocumentResources)
case class DCSearchResult(documents: Seq[clustering.DCDocument])

// Single object that interfaces to Ning's Async HTTP library
object AsyncHttpRequest {  
  
  private def getHttpConfig = {
    val builder = new AsyncHttpClientConfig.Builder()
    val config = builder
        .setMaximumConnectionsTotal(16)
        .setMaximumConnectionsPerHost(20)
        .setFollowRedirects(true)
        .setCompressionEnabled(true)
        .setAllowPoolingConnection(true)
        .setRequestTimeoutInMs(20000)
        .build
   config
  }
      
  private lazy val asyncHttpClient = new AsyncHttpClient(getHttpConfig)
  
  // Since AsyncHTTPClient has an executor anyway, allow re-use (if desired) for an Akka Promise execution context
  lazy val executionContext = ExecutionContext.fromExecutor(asyncHttpClient.getConfig().executorService())
  
  // Execute an asynchronous HTTP request, with given callbacks for success and failure
  def apply(url       : String, 
            onSuccess : (Response) => Unit, 
            onFailure : (Throwable) => Unit ) = {
    
    asyncHttpClient.prepareGet(url).execute(
      new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
         onSuccess(response)
         response
        }
        override def onThrowable(t: Throwable) = {
          onFailure(t)
        }
      }) 
  }
  
  // Version that returns a Future[Response]
  def apply(url:String) : Future[Response] = {  
    
    implicit val context = executionContext
    var promise = Promise[Response]()         // uses executionContext derived from asyncClient executor
    
    asyncHttpClient.prepareGet(url).execute(
      new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
          promise.success(response)
          response
        }
        override def onThrowable(t: Throwable) = {
          promise.failure(t)
        }
      })
      
    promise
  }
  
  // You probably shouldn't ever call this :)
  // it will block the process
  def BlockingHttpRequest(url:String) : String = {
      val f = asyncHttpClient.prepareGet(url).execute()
      f.get().getResponseBody()      // blocks until result comes back. needs async() so it doesn't tie up thread  
  }
}

class DocumentSetIndexer(var documentSet:DocumentSet) {

  implicit val context = AsyncHttpRequest.executionContext  
  private val vectorGen = new DocumentVectorGenerator    
  
  private def printElapsedTime(op:String, t0 : Long) {
    val t1 = System.nanoTime()
    println(op + ", time: " + (t1 - t0)/1e9 + " seconds")
  }

  case class DocAndTerms(doc: DCDocument, terms: Seq[String])
  
  var numDocsRetrieved = 0
  
  // Given retrieved text, lex it and store the result. Purely functional, can be parallelized
  private def MakeTerms(doc:DCDocument, response:Response) = {
    numDocsRetrieved += 1
    println("WORKER: " + numDocsRetrieved + " docs retrieved, " + doc.resources.text)
    val text = response.getResponseBody
    DocAndTerms(doc, Lexer.makeTerms(text))
  }

  // Given a list of DocAndTerms, create each one, write it to the DB, add it to the vector generator
  // This function sequences all of the operations with side effects
  private def MakeDocs(docList : Seq[DocAndTerms]) = {
    for (dt <- docList) {
      val newDoc = new Document(dt.doc.title, dt.doc.resources.text, dt.doc.canonical_url)
      documentSet.addDocument(newDoc)
      newDoc.save()
      vectorGen.AddDocument(newDoc.id, dt.terms)
    }
    vectorGen.DocumentVectors()
  }
  
  // Query documentCloud and create one document object for each doc returned by the query
  def RetrieveAndIndexDocuments() : Future[DocumentSetVectors] = {     
    // Blocking document list retrieval for the moment
    val num = documentSet.query.filter(_.isDigit)
    val q = documentSet.query.filter(c => !(c.isDigit))
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json?per_page=" + num + "&q=" + q
    val response = AsyncHttpRequest.BlockingHttpRequest(documentCloudQuery)
    val result = parse[DCSearchResult](response)              // TODO error handling, log bad result from DC here
    
    println("WORKER: Got document set result with " + result.documents.size + " docs.")
    
    // Generate Futures for each document we want to retrieve
    var docFutures = List[(DCDocument, Future[Response])]()
    for (i <- 0 to result.documents.size-1) {
      val doc = result.documents.get(i)
      docFutures = (doc,AsyncHttpRequest(doc.resources.text)) :: docFutures
      //if ((i % 7) == 0) Thread.sleep(1000)
    }
    //val docFutures = result.documents.map { doc => (doc,AsyncHttpRequest(doc.resources.text)) }
    
    // When each doc retrieval completes, feed it into MakeTerms
    val addedDocFutures = docFutures map { case(doc, futureResponse) => futureResponse map { response => MakeTerms(doc, response) } }   
      
    // Generate a future that waits for all futures to complete...
    val docSetFuture = Future.sequence(addedDocFutures)
    
    // When they do, create a Document object for each returned doc, and TF-IDF index. Return final document vectors
    docSetFuture map MakeDocs
  }

  
  def BuildTree() = {
    val t0 = System.nanoTime()
    val vectorsPromise = RetrieveAndIndexDocuments()
    
    vectorsPromise onSuccess {
      case docSetVecs:DocumentSetVectors => 
        printElapsedTime("WORKER: Retrieved and indexed " + documentSet.documents.size + " documents", t0)

        val t1 = System.nanoTime()
        val docTree = BuildDocTree(docSetVecs)
        printElapsedTime("WORKER: Clustered documents", t1)
        //println(docTree.prettyString)
        
        val t2 = System.nanoTime()
        documentSet.update();            
        printElapsedTime("WORKER: Saved DocumentSet to DB", t2)
    }
  } 
}