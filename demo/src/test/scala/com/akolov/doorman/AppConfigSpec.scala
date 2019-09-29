package com.akolov.doorman

import org.specs2.mutable.Specification

class AppConfigTest extends Specification {

  "Application Configuration" >> {
    "read oauth configurations" >> {
      uriReturns200()
    }
  }

  private[this] def uriReturns200() = {

    val googleConfig = AppConfig.oauthEntries(AppConfig.config).get("google")

    googleConfig.get.userAuthorizationUri must beEqualTo("https://accounts.google.com/o/oauth2/v2/auth")
    googleConfig.get.clientId must not be empty
  }

}
