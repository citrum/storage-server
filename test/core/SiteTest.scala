package core
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}

class SiteTest extends FunSuite with TableDrivenPropertyChecks with Matchers {
  test("isSubDomain") {
    Table(("domain", "sub", "result")
      , ("example.com", "example.com", false)
      , ("example.com", "a", false)
      , ("example.com", ".example.com", false)
      , ("example.com", "a.example.com", true)
      , ("example.com", "sub.example.com", true)
      , ("example.com", "a.another.com", false)
    ).forEvery {case (domain: String, sub: String, result: Boolean) =>
      Site.isSubDomain(domain, sub) shouldEqual result
    }
  }
}
