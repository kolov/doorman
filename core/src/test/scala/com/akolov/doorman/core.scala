package com.akolov.doorman

import com.akolov.doorman.core.OAuthEndpoints
import org.specs2.mutable._
import io.circe._
import io.circe.parser._


class OAuthEndpointsSpec extends Specification {
  "jsonToMap" should {
    "Render" in {
      val rawJson = """{
                      | "a": 1,
                      | "b": "bbb"
                      |}""".stripMargin

      val r = decode[JsonObject](rawJson) map { (json: JsonObject) =>
        OAuthEndpoints.jsonToMap(json)
      }

//        === Right(Map("a" -> "1", "b" -> "bbb"))
    }
  }
}
