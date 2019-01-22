package com.akolov.doorman

import org.specs2.mutable.Specification

class AppConfigTest extends Specification {

  "Application Configuration" >> {
    "read oauth configurations" >> {
      uriReturns200()
    }
  }

  private[this] def uriReturns200() =
    AppConfig.oauthEntries(AppConfig.config) must beEqualTo(
      Map("google" ->
        OauthConfig(
          userAuthorizationUri = "https://accounts.google.com/o/oauth2/v2/auth",
          accessTokenUri = "https://www.googleapis.com/oauth2/v4/token",
          userInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo",
          clientId = "set in env var",
          clientSecret = "set in env var",
          scope = List("openid", "email", "profile"),
          redirectUrl = "http:/localhost:8080/oauth/login")))


}
