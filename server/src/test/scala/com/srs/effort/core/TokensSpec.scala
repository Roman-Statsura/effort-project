package com.srs.effort.core

import cats.effect.*
import doobie.postgres.implicits.*
import doobie.implicits.*
import cats.implicits.*
import org.scalatest.freespec.AsyncFreeSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.*

import com.srs.effort.fixtures.*
import com.srs.effort.domain.pagination.*
import com.srs.effort.config.*

class TokensSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with DoobieSpec
    with UserFixture {

  val initScript: String   = "sql/recoverytokens.sql"
  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  "Tokens 'algebra'" - {
    "should not create a new token for a non-existing user" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(1000000L))
          token  <- tokens.getToken("somebody@email.com")
        } yield token

        program.asserting(_ shouldBe None)
      }
    }

    "should not create a new token for aт existing user" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(1000000L))
          token  <- tokens.getToken(romanEmail)
        } yield token

        program.asserting(_ shouldBe defined)
      }
    }
  

    "should not validate expired tokens" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(100L))
          maybeToken  <- tokens.getToken(romanEmail)
          _ <- IO.sleep(500.millis)
          isTokenValid <- maybeToken match {
            case Some(token) => tokens.checkToken(romanEmail, token)
            case None => IO.pure(false)
          }  
        } yield isTokenValid

        program.asserting(_ shouldBe false)
      }
    }

    "should validate tokens that have not expired yet" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(1000000L))
          maybeToken  <- tokens.getToken(romanEmail)
          isTokenValid <- maybeToken match {
            case Some(token) => tokens.checkToken(romanEmail, token)
            case None => IO.pure(false)
          }  
        } yield isTokenValid

        program.asserting(_ shouldBe true)
      }
    }

    "should only validate tokens for the user generated them" in {
      transactor.use { xa =>
        val program = for {
          tokens <- LiveTokens[IO](mockedUsers)(xa, TokenConfig(1000000L))
          maybeToken  <- tokens.getToken(romanEmail)
          isRomanTokenValid <- maybeToken match {
            case Some(token) => tokens.checkToken(romanEmail, token)
            case None => IO.pure(false)
          }  

          isOtherTokenValid <- maybeToken match {
            case Some(token) => tokens.checkToken("someoneelse@gmail.com", token)
            case None => IO.pure(false)
          } 
        } yield (isRomanTokenValid, isOtherTokenValid)

        program.asserting {
          case (isRomanTokenValid, isOtherTokenValid) =>
            isRomanTokenValid shouldBe true
            isOtherTokenValid shouldBe false
        }
      }
    }
  }
}
