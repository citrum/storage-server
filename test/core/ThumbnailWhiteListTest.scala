package core
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}

class ThumbnailWhiteListTest extends FunSuite with TableDrivenPropertyChecks with Matchers {
  test("parseName") {
    Table(("name", "suffixAndParams")
      , ("4enif9q778.png", None)
      , ("4enif9q778~100x100.png", Some("~100x100"))
      , ("4enif9q778~100x100.grow.000000.png", Some("~100x100.grow.000000"))
      , ("4enif9q778.portfolio.png", Some(".portfolio"))
      , ("4enif9q778.res~140x140.png", Some(".res~140x140"))
      , ("4enif9q778.portfolio~3000x2000~grow.ffffff.png", Some(".portfolio~3000x2000~grow.ffffff"))
    ).forEvery {case (name: String, result: Option[String]) =>
      ThumbnailWhiteList.parseName(name) shouldEqual result
    }
  }
}
