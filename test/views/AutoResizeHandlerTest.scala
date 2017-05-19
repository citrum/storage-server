package views
import core.{ImageSize, ResizePolicy}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import views.AutoResizeHandler.Params

class AutoResizeHandlerTest extends FunSuite with TableDrivenPropertyChecks with Matchers {

  case class Pars(size: ImageSize, policy: ResizePolicy.Value = ResizePolicy.Crop, resizeGrowColor: Int = 0) {
    def doMatch(p: Params) {
      size shouldEqual p.size
      policy shouldEqual p.policy
      resizeGrowColor shouldEqual p.resizeGrowColor
    }
  }

  test("resize property") {
    Table[String, Option[Pars]](
      ("gotParams", "result")
      , ("", None)
      , ("~", None)
      , ("~10x10", None)
      , ("10x10~", None)
      , ("0x0", None)
      , ("5x0", None)
      , ("5x-1", None)
      , ("-1x5", None)
      , ("5x", None)
      , ("x5", None)
      , (" 5x5", None)
      , ("5 x5", None)
      , ("5x 5", None)
      , ("5x5 ", None)
      , ("10x20", Some(Pars(ImageSize(10, 20))))
      , ("10x20~5x5", None)
      , ("33x15~fit", Some(Pars(ImageSize(33, 15), ResizePolicy.Fit)))
      , ("fit", None)
      , ("33x15~crop", None)
      , ("33x15~grow", None)
      , ("33x15~grow.0", None)
      , ("33x15~grow.000", None)
      , ("33x15~grow.000000", Some(Pars(ImageSize(33, 15), ResizePolicy.Grow, 0)))
      , ("33x15~grow.23ffca", Some(Pars(ImageSize(33, 15), ResizePolicy.Grow, 0x23ffca)))
      , ("33x15~grow.23ffca~fit", None)
      , ("33x15~fit~grow.23ffca", None)
    ).forEvery {case (gotParams: String, result: Option[Pars]) =>
      val params = new AutoResizeHandler.Params
      params.parse(gotParams) match {
        case Left(msg) => result shouldBe None
        case Right(()) =>
          result shouldBe defined
          result.get.doMatch(params)
      }
    }
  }
}
