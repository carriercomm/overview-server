package org.overviewproject.jobhandler.filegroup.task

trait PdfDocument {
  def pages: Iterable[PdfPage]
  def text: String
  def close(): Unit
}

case class PdfPage(data: Array[Byte], text: String)
