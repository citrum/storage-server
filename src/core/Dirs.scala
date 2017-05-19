package core
import java.nio.file.{Files, Path}

import core.util.NameGen
import org.apache.commons.lang3.StringUtils

object Dirs {
  val baseDir = Conf.baseDir
  val store = new Naming(baseDir.resolve("store"), "", nameLength = 10, splitFirstChunks = 2)
  val tmp = new Naming(baseDir.resolve("tmp"), "tmp/", nameLength = 6, splitFirstChunks = 2, addHourPrefix = true)

  /**
    * Класс, описывающий хранение файлов по каталогам и генерацию их имён.
    *
    * @param baseDir          Базовый каталог для хранения
    * @param externalPath     Внешний путь для этих файлов (url до файла составляется из externalPath + name + "." + ext)
    * @param nameLength       Длина имени (без учёта часового префикса)
    * @param splitFirstChunks Сколько частей по 2 символа в начале имени нужно выделить на подкаталоги.
    * @param addHourPrefix    Добавить префикс текущего часа (всегда 2 символа) к имени?
    */
  class Naming(val baseDir: Path, val externalPath: String, val nameLength: Int, splitFirstChunks: Int, addHourPrefix: Boolean = false) {

    /**
      * Важно! Сгенерированное имя и суффикс не должны содержать символов '~', '.'
      */
    def getNewNameWithoutExt(dotSuffix: String, ext: String): String = {
      if (dotSuffix.nonEmpty) checkSuffixOrName(dotSuffix.substring(1), "suffix")
      while (true) {
        val simpleName: String = NameGen.generateName(nameLength)
        checkSuffixOrName(simpleName, "generated name")
        var name: String = simpleName + dotSuffix
        if (addHourPrefix) name = getHourPrefix + name
        if (!Files.exists(toPath(name + "." + ext))) return name
      }
      null
    }

    def splitToPath(name: String): String = {
      val sb = new java.lang.StringBuilder(name.length + splitFirstChunks)
      var i = 0
      val maxi = splitFirstChunks * 2
      while (i < maxi) {
        sb append name.substring(i, i + 2)
        sb append '/'
        i += 2
      }
      sb append name.substring(i)
      sb.toString
    }

    def toPath(nameWithSuffix: String): Path = baseDir.resolve(splitToPath(nameWithSuffix))

    private def getHourPrefix: String = {
      val intPrefix = System.currentTimeMillis() / (3600 * 1000) % 24
      StringUtils.leftPad(String.valueOf(intPrefix), 2, '0')
    }

    private def checkSuffixOrName(s: String, varName: String): Unit = {
      require(!StringUtils.containsAny(s, '~', '.'), varName + " contains forbidden char")
    }
  }
}
