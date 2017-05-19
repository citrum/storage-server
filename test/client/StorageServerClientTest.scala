package client
import client.StorageServerClient.ThumbnailParams
import core.{ImageSize, ResizePolicy}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}

class StorageServerClientTest extends FunSuite with TableDrivenPropertyChecks with Matchers {
  test("ThumbnailParams") {
    Table[ThumbnailParams, String](("params", "result")
      , (ThumbnailParams(ImageSize(1, 1)), "1x1")
      , (ThumbnailParams(ImageSize(20, 35)), "20x35")
      , (ThumbnailParams(ImageSize(20, 35), ResizePolicy.Crop), "20x35")
      , (ThumbnailParams(ImageSize(20, 35), ResizePolicy.Fit), "20x35~fit")
      , (ThumbnailParams(ImageSize(20, 35), ResizePolicy.Grow), "20x35~grow.000000")
      , (ThumbnailParams(ImageSize(20, 35), ResizePolicy.Grow, 0x93ab5e), "20x35~grow.93ab5e")
    ).forEvery {case (tnParams: ThumbnailParams, result: String) =>
      tnParams.toString shouldEqual result
    }

    the[IllegalArgumentException] thrownBy ThumbnailParams(ImageSize(0, 0)) getMessage() should include("Invalid size")
    the[IllegalArgumentException] thrownBy ThumbnailParams(ImageSize(10, 0)) getMessage() should include("Invalid size")
    the[IllegalArgumentException] thrownBy ThumbnailParams(ImageSize(-5, 10)) getMessage() should include("Invalid size")
    the[IllegalArgumentException] thrownBy ThumbnailParams(ImageSize(1, 2), ResizePolicy.Crop, 1) getMessage() should include("resizeGrowColor")
    the[IllegalArgumentException] thrownBy ThumbnailParams(ImageSize(20, 35), ResizePolicy.Grow, 0x193ab5e) getMessage() should include("resizeGrowColor")
  }

  test("ThumbnailParams forPath") {
    ThumbnailParams(ImageSize(20, 35), ResizePolicy.Grow, 0x93ab5e)
      .forPath("/file/", "40qgablk5b.res.png") shouldEqual "/file/40qgablk5b.res~20x35~grow.93ab5e.png"

    ThumbnailParams(ImageSize(20, 35), asJpeg = true)
      .forPath("/file/", "40qgablk5b.res.png") shouldEqual "/file/40qgablk5b.res~20x35.jpg"
  }
}
