package core.util
import java.io._
import java.nio.file.{Files, Path}

import com.google.common.io.ByteStreams

object IOUtils {

  def writeString(path: Path, str: String) {
    val s: OutputStream = Files.newOutputStream(path)
    s.write(str.getBytes)
    s.close()
  }

  def readString(path: Path): String = {
    val s: InputStream = Files.newInputStream(path)
    val bytes: Array[Byte] = ByteStreams.toByteArray(s)
    s.close()
    new String(bytes)
  }
}
