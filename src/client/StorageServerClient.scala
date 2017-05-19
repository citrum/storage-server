package client
import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty}
import core.{ImageSize, ResizePolicy}
import org.apache.commons.lang3.StringUtils

object StorageServerClient {
  /**
    * Токен загружаемого файла, хранящий все параметры загрузки.
    *
    * @param strictlyJpg       true - если допустимо загружать только jpg,
    *                          false - можно загружать любой файл, поддерживаемый классом FileExt.
    * @param allowedExtensions Если указан, то этот список ограничивает возможные загружаемые расширения файла.
    *                          В случае, если требуется загрузить только jpg, то нужно установить флаг [[strictlyJpg]], а этот список не указывать.
    * @param maxSize           Максимальный размер картинки.
    *                          При превышении этого размера, картинка будет уменьшена с сохранением пропорций так, чтобы
    *                          вписаться в этот размер.
    * @param resizePolicyEnum  Политика уменьшения картинки до размера [[maxSize]].
    * @param resizeGrowColor   Цвет RGB, которым будут заполнены поля при политике уменьшения [[ResizePolicy.Grow]].
    *                          Например, красный цвет задаётся так: 0xff0000.
    * @param maxStoredSize     Максимальный размер файла, который будет храниться на сервере.
    *                          Если загружается не jpg, то сервер не даст загрузить файл больше этого значения.
    *                          Если загружается jpg большего размера (но не более [[jpgMaxUploadSize]]), то сервер уменьшит её размер примерно до [[maxStoredSize]].
    * @param jpgMaxUploadSize  При загрузке jpg это максимальный размер файла, который сервер может принять.
    */
  case class Token(strictlyJpg: Boolean = false,
                   allowedExtensions: Option[Array[String]] = None,
                   maxSize: Option[ImageSize] = None,
                   @JsonIgnore resizePolicyEnum: ResizePolicy.Value = ResizePolicy.Fit,
                   resizeGrowColor: Int = 0, // чёрный
                   maxStoredSize: Int = 2 * 1000000,
                   jpgMaxUploadSize: Int = 20 * 1000000) {
    @JsonProperty def resizePolicy: String = resizePolicyEnum.toString
  }


  /**
    * Параметры получения превьюшки (thumbnail)
    *
    * @param size            Размер, обязательный параметр.
    * @param policy          Политика уменьшения картинки с сохранением пропорций.
    * @param resizeGrowColor Цвет полей для политики [[ResizePolicy.Grow]].
    * @param asJpeg          Если указан, то превьюшка всегда запрашивается как jpg картинка, независимо от исходного формата.
    */
  case class ThumbnailParams(size: ImageSize,
                             policy: ResizePolicy.Value = ResizePolicy.Crop,
                             resizeGrowColor: Int = 0,
                             asJpeg: Boolean = false) {
    require(size.isValid, "Invalid size")
    if (resizeGrowColor != 0) {
      require(policy == ResizePolicy.Grow, "resizeGrowColor can only be set for ResizePolicy.Grow policy")
      require((resizeGrowColor & 0xff000000) == 0, "Invalid resizeGrowColor")
    }

    def writeToSb(sb: java.lang.StringBuilder): java.lang.StringBuilder = {
      sb append size.w append 'x' append size.h
      policy match {
        case ResizePolicy.Crop => // default value
        case ResizePolicy.Fit => sb append "~fit"
        case ResizePolicy.Grow => sb append "~grow." append StringUtils.leftPad(Integer.toHexString(resizeGrowColor), 6, '0')
      }
      sb
    }

    override def toString: String = writeToSb(new java.lang.StringBuilder()).toString

    /**
      * Получить путь до этого thumbnail
      *
      * @param prefix Базовый путь, он просто вставляется перед path, например "/file/"
      * @param path Сам путь с расширением (расширение обязательно), в который нужно вставить параметры, например "40qgablk5b.res.jpg"
      * @return Результирующий путь, например "/file/40qgablk5b.res~180x180.jpg"
      */
    def forPath(prefix: String, path: String): String = {
      val sb = new java.lang.StringBuilder(32)
      val idx = path.lastIndexOf('.')
      sb append prefix append path.substring(0, idx) append '~'
      writeToSb(sb)

      if (asJpeg) sb append ".jpg"
      else sb append path.substring(idx)

      sb toString()
    }
  }

  /** Белый список форматов превьюшек, составленный из suffix+params. */
  case class ThumbnailWhiteList(set: Set[String])
}
