package core.util
import core.Token
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.multipart.FileUpload
import org.apache.commons.lang3.StringUtils

object FileExt {
  def detectAndValidate(upload: FileUpload, token: Token): Option[String] = {
    val ext: String = StringUtils.substringAfterLast(upload.getFilename, ".").toLowerCase
    val chunk: ByteBuf = upload.getChunk(8)
    val valid =
      if (chunk.readableBytes() >= 8 && validate(chunk, ext)) {
        token.allowedExtensions match {
          case Some(allowed) => allowed.contains(ext)
          case None => true
        }
      } else false
    if (valid) Some(ext) else None
  }

  // Supported formats:
  // images: jpg, gif, png, svg, ai, cdr, eps
  // docs: pdf, doc, docx, odt
  // archives: zip, 7z
  def validate(data: ByteBuf, ext: String): Boolean = {
    ext match {
      case "jpg" | "jpeg" => data.getUnsignedShort(0) == 0xffd8 // FF D8
      case "zip" | "docx" | "odt" => data.getUnsignedShort(0) == 0x504b // PK
      case "gif" => data.getInt(0) == 0x47494638 // GIF8
      case "pdf" => data.getInt(0) == 0x25504446 // %PDF
      case "doc" => data.getInt(0) == 0xD0CF11E0
      case "rtf" => data.getInt(0) == 0x7b5c7274 // \{rt
      case "cdr" => data.getInt(0) == 0x52494646 // RIFF
      case "ai" => data.getInt(0) == 0x25215053 // %!PS
      case "7z" => data.getInt(0) == 0x377abcaf // 7z BC AF
      case "png" => data.getLong(0) == 0x89504e470d0a1a0aL // \211 P N G \r \n \032 \n
      case "svg" => val magic = data.getInt(0); magic == 0x3c737667 || magic == 0x3c3f786d // <svg or <?xm
      case "eps" => val magic = data.getInt(0); magic == 0x25215053 || magic == 0xC5D0D3C6 // %!PS or ÅÐÓÆ
      case _ => false
    }
  }

  def isJpg(ext: String): Boolean = ext == "jpg" || ext == "jpeg"
  def isImage(ext: String): Boolean = isJpg(ext) || ext == "png" || ext == "gif"

  def getMimeType(ext: String): String = ext match {
    case "jpg" | "jpeg" => "image/jpeg"
    case "png" => "image/png"
    case "gif" => "image/gif"
  }
}
