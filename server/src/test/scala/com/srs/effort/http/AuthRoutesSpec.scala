package com.srs.effort.http.routes

import cats.effect.*
import cats.implicits.*
import cats.data.*
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.scalatest.freespec.AsyncFreeSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import tsec.mac.jca.HMACSHA256
import tsec.authentication.IdentityStore
import tsec.authentication.JWTAuthenticator
import tsec.jws.mac.JWTMac
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash

import scala.concurrent.duration.*

import com.srs.effort.domain.pagination.*
import com.srs.effort.core.*
import com.srs.effort.domain.user.*
import com.srs.effort.domain.auth.*
import com.srs.effort.domain.security.*
import com.srs.effort.fixtures.*
import org.typelevel.ci.CIStringSyntax
import org.http4s.headers.Authorization

class AuthRoutesSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with UserFixture
    with SecuredRouteFixture {

  ////////////////////////////////////////////////////////////////////////////////
  // prep
  ////////////////////////////////////////////////////////////////////////////////

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val mockedAuth: Auth[IO] = probedAuth(None)

  def probedAuth(userMap: Option[Ref[IO, Map[String, String]]]): Auth[IO] = new Auth[IO] {
    override def login(email: String, password: String): IO[Option[User]] =
      if (email == romanEmail && password == romanPassword)
        IO(Some(Roman))
      else IO.pure(None)

    override def signUp(newUserinfo: NewUserInfo): IO[Option[User]] =
      if (newUserinfo.email == kalyamEmail)
        IO.pure(Some(Kalyam))
      else IO.pure(None)

    override def changePassword(
        email: String,
        newPasswordInfo: NewPasswordInfo
    ): IO[Either[String, Option[User]]] =
      if (email == romanEmail)
        if (newPasswordInfo.oldPassword == romanPassword)
          IO.pure(Right(Some(Roman)))
        else
          IO.pure(Left("Invalid password"))
      else
        IO.pure(Right(None))

    override def delete(email: String): IO[Boolean] = IO.pure(true)

    override def sendPasswordRecoveryToken(email: String): IO[Unit] =
      userMap
        .traverse { userMapRef =>
          userMapRef.modify { userMap =>
            (userMap + (email -> "abc123"), ())
          }
        }
        .map(_ => ())

    override def recoverPasswordFromToken(
        email: String,
        token: String,
        newPassword: String
    ): IO[Boolean] =
      userMap
        .traverse { userMapRef =>
          userMapRef.get
            .map { userMap =>
              userMap.get(email).filter(_ == token)
            }
            .map(_.nonEmpty)
        }
        .map(_.getOrElse(false))

  }
  val authRoutes: HttpRoutes[IO] = AuthRoutes[IO](mockedAuth, mockedAuthenticator).routes

  ////////////////////////////////////////////////////////////////////////////////
  // tests
  ////////////////////////////////////////////////////////////////////////////////

  "AuthRoutes" - {
    "should return a 401 - Unauthorized if login fails" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(romanEmail, "wrongpassword"))
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 200 - OK + JWT if login is successful" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(romanEmail, romanPassword))
        )
      } yield {
        response.status shouldBe Status.Ok
        response.headers.get(ci"Authorization") shouldBe defined
      }
    }

    "should return a 400 - Bad Request if the user to create already exists" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(NewUserRoman)
        )
      } yield {
        response.status shouldBe Status.BadRequest
      }
    }

    "should return a 201 - Created if the user creation succeeds" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(NewUserKalyam)
        )
      } yield {
        response.status shouldBe Status.Created
      }
    }

    "should return a 200 - OK if logging out with a valid JWT" in {
      for {
        jwtToken <- mockedAuthenticator.create(romanEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
            .withBearerToken(jwtToken)
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "should return a 401 - Unauthorized if logging out without a valid JWT" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    // change password - user doesn't exist => 404
    "should return a 404 - Not found if changing password for a user that doesn't exist" in {
      for {
        jwtToken <- mockedAuthenticator.create(kalyamEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo(kalyamPassword, "newpassword"))
        )
      } yield {
        response.status shouldBe Status.NotFound
      }
    }

    // change password - invalid old password => 403 Forbidden
    "should return a 403 - Forbidden if old password is incorrect" in {
      for {
        jwtToken <- mockedAuthenticator.create(romanEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo("wrondpassword", "newpassword"))
        )
      } yield {
        response.status shouldBe Status.Forbidden
      }
    }

    // change password - user JWT is invalid => 401 Unauthorized
    "should return a 401 - Unauthorized if changing password without a JWT" in {
      for {
        jwtToken <- mockedAuthenticator.create(romanEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withEntity(NewPasswordInfo(romanPassword, "newpassword"))
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    // change password - happy path => 200 Ok
    "should return a 200 - OK if changing password wit valid JWT and password" in {
      for {
        jwtToken <- mockedAuthenticator.create(romanEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo(romanPassword, "newpassword"))
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "should return a 401 - Unauthorized if a non-admin tries to delete a user" in {
      for {
        jwtToken <- mockedAuthenticator.create(kalyamEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/auth/users/srs@yandex.ru")
            .withBearerToken(jwtToken)
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 200 - Ok if a admin tries to delete a user" in {
      for {
        jwtToken <- mockedAuthenticator.create(romanEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/auth/users/srs@yandex.ru")
            .withBearerToken(jwtToken)
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "should return a 200 - Ok when resetting a password, and an email should be triggered" in {
      for {
        userMapRef <- Ref.of[IO, Map[String, String]](Map())
        auth       <- IO(probedAuth(Some(userMapRef)))
        routes     <- IO(AuthRoutes(auth, mockedAuthenticator).routes)
        response <- routes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/reset")
            .withEntity(ForgotPasswordInfo(romanEmail))
        )
        userMap <- userMapRef.get
      } yield {
        response.status shouldBe Status.Ok
        userMap should contain key (romanEmail)
      }
    }

    "should return a 200 - Ok when recovering a password for a correct user/token combination" in {
      for {
        userMapRef <- Ref.of[IO, Map[String, String]](Map(romanEmail -> "abc123"))
        auth       <- IO(probedAuth(Some(userMapRef)))
        routes     <- IO(AuthRoutes(auth, mockedAuthenticator).routes)
        response <- routes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/recover")
            .withEntity(RecoverPasswordInfo(romanEmail, "abc123", "lala"))
        )
        userMap <- userMapRef.get
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "should return a 403 - Forbidden when recovering a password for a user with an incorrect token" in {
      for {
        userMapRef <- Ref.of[IO, Map[String, String]](Map(romanEmail -> "abc123"))
        auth       <- IO(probedAuth(Some(userMapRef)))
        routes     <- IO(AuthRoutes(auth, mockedAuthenticator).routes)
        response <- routes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/recover")
            .withEntity(RecoverPasswordInfo(romanEmail, "wrongtoken", "lala"))
        )
        userMap <- userMapRef.get
      } yield {
        response.status shouldBe Status.Forbidden
      }
    }
  }
}
