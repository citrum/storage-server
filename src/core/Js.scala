package core
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object Js {

  val mapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
}
