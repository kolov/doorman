package com.akolov.doorman.core.logic

import cats.implicits._
import com.akolov.doorman.core._
import org.http4s.{Query, Uri}

object LoginLogic {

  def login(config: OAuthProviderConfig): Either[DoormanError, Uri] =
    for {
      base <- Uri.fromString(config.userAuthorizationUri).leftMap(e => ConfigurationError(e.message))
      uri = Uri(
        base.scheme,
        base.authority,
        base.path,
        Query(
          ("redirect_uri", Some(config.redirectUrl)),
          ("client_id", Some(config.clientId)),
          ("response_type", Some("code")),
          ("scope", Some(config.scope.mkString(" ")))
        ),
        base.fragment
      )
    } yield uri

}
