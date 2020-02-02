package com.akolov.doorman

import com.akolov.doorman.demo.{AppConfig}
import org.specs2.mutable.Specification

class AppConfigTest extends Specification {

  "Application Configuration" >> {
    "read oauth configurations" >> {
      val googleConfig = AppConfig.demoAppConfig.right.get.oauthProviders.get("google").get

      googleConfig.userAuthorizationUri must beEqualTo("https://accounts.google.com/o/oauth2/v2/auth")
      googleConfig.clientId must not be empty
    }
  }

}
