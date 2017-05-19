package core

object ErrorMessages {
  val needJpg = "Недопустимый тип файла. Загрузите изображение формата jpg."
  val invalidExt = "Недопустимый формат файла."
  val invalidImage = "Некорректное изображение."
  val imageProcessingError = "Ошибка обработки изображения."
  val emptyFile = "Загружен пустой файл"

  def tooBig(maxSize: Int) = "Загружаемый файл слишком большого размера.<br>Максимальный размер файла: " + (maxSize / 1000000) + " МБ"
}
