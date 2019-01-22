package com.akolov.doorman

import java.util.UUID

import cats.effect.Sync

sealed trait UserIdentity

case class OauthUser(id: String) extends UserIdentity

case class AppUser(uuid: String, userIdentity: Option[UserIdentity] = None)

object AppUser {

  def create[F[_] : Sync]: F[AppUser] = Sync[F].pure(new AppUser(UUID.randomUUID.toString))

  def forProvider(uuid: String, provider: Option[String]): AppUser = new AppUser(uuid, provider.map(OauthUser(_)))
}

