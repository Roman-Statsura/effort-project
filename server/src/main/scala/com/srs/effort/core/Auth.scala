package com.srs.effort.core

import cats.*
import cats.implicits.*
import cats.effect.*
import cats.data.OptionT
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.*
import doobie.util.fragment.Fragment
import java.util.UUID
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

import tsec.authentication.AugmentedJWT
import tsec.authentication.JWTAuthenticator
import tsec.authentication.BackingStore
import tsec.authentication.IdentityStore
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash
import tsec.common.SecureRandomId

import com.srs.effort.domain.security.*
import com.srs.effort.domain.auth.*
import com.srs.effort.domain.user.*
import com.srs.effort.config.*

trait Auth[F[_]] {
  def login(email: String, password: String): F[Option[User]]
  def signUp(newUserinfo: NewUserInfo): F[Option[User]]
  def changePassword(
      email: String,
      newPasswordInfo: NewPasswordInfo
  ): F[Either[String, Option[User]]]
  def delete(email: String): F[Boolean]
  // allow password recovery
  def sendPasswordRecoveryToken(email: String): F[Unit]
  def recoverPasswordFromToken(email: String, token: String, newPassword: String): F[Boolean]
}

class LiveAuth[F[_]: Async: Logger] private (
    users: Users[F],
    tokens: Tokens[F],
    emails: Emails[F]
) extends Auth[F] {
  override def login(email: String, password: String): F[Option[User]] =
    for {
      maybeUser <- users.find(email)
      maybeValidatedUser <- maybeUser.filterA(user =>
        BCrypt
          .checkpwBool[F](
            password,
            PasswordHash[BCrypt](user.hashedPassword)
          )
      )
    } yield maybeValidatedUser

  override def signUp(newUserinfo: NewUserInfo): F[Option[User]] =
    users.find(newUserinfo.email).flatMap {
      case Some(_) => None.pure[F]
      case None =>
        for {
          hashedPassword <- BCrypt.hashpw[F](newUserinfo.password)
          user <- User(
            newUserinfo.email,
            hashedPassword,
            newUserinfo.firstName,
            newUserinfo.lastName,
            newUserinfo.company,
            Role.RECRUITER
          ).pure[F]
          _ <- users.create(user)
        } yield Some(user)
    }

  override def changePassword(
      email: String,
      newPasswordInfo: NewPasswordInfo
  ): F[Either[String, Option[User]]] = {
    def checkAndUpdate(
        user: User,
        oldPassword: String,
        newPassword: String
    ): F[Either[String, Option[User]]] =
      for {
        passCheck <- BCrypt
          .checkpwBool[F](
            newPasswordInfo.oldPassword,
            PasswordHash[BCrypt](user.hashedPassword)
          )
        updateResult <-
          if (passCheck) updateUser(user, newPassword).map(Right(_))
          else Left("Invalid password").pure[F]
      } yield updateResult

    users.find(email).flatMap {
      case None => Right(None).pure[F]
      case Some(user) =>
        val NewPasswordInfo(oldPassword, newPassword) = newPasswordInfo
        checkAndUpdate(user, oldPassword, newPassword)
    }
  }

  override def delete(email: String): F[Boolean] =
    users.delete(email)

  // password recovery
  override def sendPasswordRecoveryToken(email: String): F[Unit] =
    tokens.getToken(email).flatMap {
      case Some(token) => emails.sendPasswordRecoveryEmail(email, token)
      case None        => ().pure[F]
    }

  override def recoverPasswordFromToken(
      email: String,
      token: String,
      newPassword: String
  ): F[Boolean] = for {
    maybeUser    <- users.find(email)
    tokenIsValid <- tokens.checkToken(email, token)
    result <- (maybeUser, tokenIsValid) match {
      case (Some(user), true) => updateUser(user, newPassword).map(_.nonEmpty)
      case _                  => false.pure[F]
    }
  } yield result

  // private
  private def updateUser(user: User, newPassword: String): F[Option[User]] = for {
    hashedPassword <- BCrypt.hashpw[F](newPassword)
    updatedUser    <- users.update(user.copy(hashedPassword = hashedPassword))
  } yield updatedUser
}

object LiveAuth {
  def apply[F[_]: Async: Logger](
      users: Users[F],
      tokens: Tokens[F],
      emails: Emails[F]
  ): F[LiveAuth[F]] =
    new LiveAuth[F](users, tokens, emails).pure[F]
}
