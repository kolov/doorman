package com.akolov.doorman

import com.akolov.doorman.demo.{AppConfig, ProvidersLookup}
import org.specs2.mutable.Specification

class AppConfigTest extends Specification {

  "Application Configuration" >> {
    "read oauth configurations" >> {
      val doormanConfig: ProvidersLookup = AppConfig.demoAppConfig.right.get
      val googleConfig = doormanConfig.forId("google").get

      googleConfig.userAuthorizationUri must beEqualTo("https://accounts.google.com/o/oauth2/v2/auth")
      googleConfig.clientId must not be empty
    }
  }

}
