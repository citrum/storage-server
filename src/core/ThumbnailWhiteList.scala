package core
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

/**
  * Белый список разрешённых форматом thumbnails.
  * Служит для защиты от множественных запросов thumbnails произвольных размеров. Такая атака
  * может вызвать высокую нагрузку на CPU и быстро заполнит место на винте лишними превьюшками.
  * По-дефолту белый список пуст. Это значит, что он разрешает любой формат.
  *
  * #suffixAndParams для файла "4enif9q778.portfolio~3000x2000~grow.ffffff.png"
  * будет равен ".portfolio~3000x2000~grow.ffffff".
  */
object ThumbnailWhiteList {
  private val log = LoggerFactory.getLogger(getClass)

  // По-дефолту все форматы разрешены (в случае пустого сета)
  private var current: Set[String] = Set.empty

  def isAllowedSuffixAndParams(suffixAndParams: String): Boolean = {
    if (current.isEmpty) true
    else current.contains(suffixAndParams)
  }

  /** Парсим имя файла, чтобы получить suffixAndParams */
  def parseName(name: String): Option[String] = {
    val idx1 = StringUtils.indexOfAny(name, '.', '~')
    val idx2 = name.lastIndexOf('.')
    if (idx1 == -1 || idx2 == -1 || idx1 == idx2) None
    else Some(name.substring(idx1, idx2))
  }

  def isAllowedName(name: String): Boolean = parseName(name) match {
    case None => false
    case Some(suffixAndParams) => isAllowedSuffixAndParams(suffixAndParams)
  }

  def setNewList(whiteList: Set[String]): Unit = {
    // К основному списку whiteList добавим этот же список без суффиксов.
    // Это нужно для временных файлов, потому что у них нет суффиксов.
    // @see http://redmine/issues/14173
    current = whiteList ++ whiteList.flatMap {item =>
      if (item.charAt(0) == '.') Seq(item.substring(item.indexOf('~')))
      else Nil
    }

    log.info(current.mkString("New whiteList: ", ", ", ""))
  }
}
