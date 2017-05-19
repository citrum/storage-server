package core.util

import scala.util.Random

/**
 * Генератор имён заданной длины, состоящих из цифр и латинских букв в нижнем регистре.
 */
object NameGen {

  def generateName(nameLength: Int): String = {
    val sb = new java.lang.StringBuilder(nameLength)
    for (i <- 1 to (nameLength / 2)) {
      val num = Random.nextInt() & 0x7ffffff
      sb append charFromInt(num & 0xffff) append charFromInt(num >> 16)
    }
    sb.toString
  }

  private def charFromInt(i: Int): Char = i % 36 match {
    case digit if digit < 10 => ('0' + digit).toChar
    case letter => (letter - 10 + 'a').toChar
  }
}
