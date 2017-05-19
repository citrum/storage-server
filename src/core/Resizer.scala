package core
import java.awt.image.{BufferedImage, ComponentColorModel}
import java.awt.{Color, Graphics2D}

import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions

/** Политика уменьшения картинки с сохранением пропорций */
object ResizePolicy extends Enumeration {
  /** Просто уменьшение картинки без обрезания и добавления полей. Лишь бы вписалась в заданный размер. */
  val Fit = Value
  /** Уменьшение картинки через обрезание по краям, оставляя только центральную часть */
  val Crop = Value
  /** После уменьшения картинки, добавить ей поля (вертикальные, либо горизонтальные) заданного цвета [[Token.resizeGrowColor]] */
  val Grow = Value
}

object Resizer {
  def resize(source: BufferedImage, size: ImageSize, policy: ResizePolicy.Value, resizeGrowColor: Int = 0): BufferedImage = {
    // Убрать альфа-канал и преобразовать 8-битный цвет к полноцвету RGB
    val preparedSrc: BufferedImage =
      source.getColorModel match {
        case cm: ComponentColorModel if !cm.hasAlpha => source
        case cm =>
          val copy = new BufferedImage(source.getWidth, source.getHeight, BufferedImage.TYPE_INT_RGB)
          val g2d: Graphics2D = copy.createGraphics()
          // Возможно, впоследствии надо будет поменять это поведение.
          // Белый фон нужен в основном при конвертации 8-битного GIF с прозрачностью.
          // Но нам пока не нужны превьюшки с прозрачностью, поэтому цвет здесь всегда белый.
          if (cm.hasAlpha) {
            g2d.setColor(Color.WHITE)
            g2d.fillRect(0, 0, copy.getWidth(), copy.getHeight())
          }
          g2d.drawImage(source, 0, 0, null)
          g2d.dispose()
          copy
      }

    policy match {
      case ResizePolicy.Fit =>
        Thumbnails.of(preparedSrc).size(size.w, size.h).asBufferedImage()

      case ResizePolicy.Crop =>
        Thumbnails.of(preparedSrc).size(size.w, size.h).crop(Positions.CENTER).asBufferedImage()

      case ResizePolicy.Grow =>
        val scaled: BufferedImage = Thumbnails.of(preparedSrc).size(size.w, size.h).asBufferedImage()
        if (scaled.getWidth != size.w || scaled.getHeight != size.h) {
          val img = new BufferedImage(size.w, size.h, BufferedImage.TYPE_INT_RGB)
          val x: Int = (size.w - scaled.getWidth) / 2
          val y: Int = (size.h - scaled.getHeight) / 2
          val color = new Color(resizeGrowColor)
          val g: Graphics2D = img.createGraphics()
          g.drawImage(scaled, x, y, color, null)
          g.setColor(color)
          if (scaled.getWidth == size.w) {
            // Дорисовать поля сверху и снизу
            if (y > 0) g.fillRect(0, 0, size.w, y)
            val y2 = y + scaled.getHeight()
            if (y2 < size.h) g.fillRect(0, y2, size.w, size.h - y2)
          } else {
            // Дорисовать поля слева и справа
            if (x > 0) g.fillRect(0, 0, x, size.h)
            val x2 = x + scaled.getWidth()
            if (x2 < size.w) g.fillRect(x2, 0, size.w - x2, size.h)
          }
          g.dispose()
          img
        } else scaled
    }
  }
}
