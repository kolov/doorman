package com.akolov.doorman
import com.akolov.doorman.core.OAuthEndpoints
import org.specs2.mutable.Specification
import io.circe._
import io.circe.parser._

class OAuthEndpointsSpec extends Specification {
  "jsonToMap" should {
    "Render" in {
      val rawJson = """{
                      | "a": 1,
                      | "b": "bbb"
                      |}""".stripMargin

      decode[JsonObject](rawJson) map {
        case json: JsonObject => OAuthEndpoints.jsonToMap(json)
      } shouldEqual Right(Map("a" -> "1", "b" -> "bbb"))
    }
  }
}
