package models.archive.zip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets

import models.archive.DosDate

/**
 * Zip Central Directory File Header. On Unix, read and write permissions are set for owner.
 *  Only for files < 4Gb.
 */
class CentralDirectoryFileHeader(fileName: String, fileSize: Long, crc: Int,  offset: Long, timeStamp: DosDate)
    extends LittleEndianWriter with ZipFormat with ZipFormatSize {

  protected val versionMadeBy = unix | zipSpecification
  protected val extractorVersion = defaultVersion
  protected val flags = useUTF8
  protected val compressedSize = fileSize.toInt
  protected val uncompressedSize = fileSize.toInt
  protected val extraFieldLength = empty
  protected val extraFieldBytes: Array[Byte] = Array.empty
  
  protected val localHeaderOffset = offset
  
  protected val fileNameSize = fileNameBytes.size

  def bytes: Array[Byte] = headerBytes ++ fileNameBytes ++ extraFieldBytes

  def size: Int = centralDirectoryHeader + extraFieldLength + fileNameSize
  
  private def headerBytes = {
    writeInt(centralFileHeaderSignature) ++
      writeShort(versionMadeBy) ++
      writeShort(extractorVersion) ++
      writeShort(flags) ++
      writeShort(noCompression) ++
      writeShort(timeStamp.time) ++
      writeShort(timeStamp.date) ++
      writeInt(crc) ++
      writeInt(compressedSize) ++
      writeInt(uncompressedSize) ++
      writeShort(fileNameSize) ++
      writeShort(extraFieldLength) ++
      writeShort(empty) ++
      writeShort(diskNumber) ++
      writeShort(empty) ++
      writeInt(readWriteFile) ++
      writeInt(localHeaderOffset.toInt)
  }
  
  private def fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8)
}
