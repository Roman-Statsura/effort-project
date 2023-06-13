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

import com.srs.effort.fixtures.*
import com.srs.effort.domain.user.*
import org.scalatest.Inside
import org.postgresql.util.PSQLException
import com.srs.effort.fixtures.*

class UsersSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Inside
    with DoobieSpec
    with UserFixture {
  override val initScript: String = "sql/users.sql"
  given logger: Logger[IO]        = Slf4jLogger.getLogger[IO]

  "Users 'algebra'" - {
    "should retrieve a user by email" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find("kalyam@yandex.ru")
        } yield retrieved

        program.asserting(_ shouldBe Some(Kalyam))
      }
    }

    "should return None if the email doesn't exist" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find("notfound@yandex.ru")
        } yield retrieved

        program.asserting(_ shouldBe None)
      }
    }

    "should create a new user" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(NewUser)
          maybeUser <- sql"""SELECT * FROM users WHERE email = ${NewUser.email}"""
            .query[User]
            .option
            .transact(xa)
        } yield (userId, maybeUser)

        program.asserting { case (userId, maybeUser) =>
          userId shouldBe NewUser.email
          maybeUser shouldBe Some(NewUser)
        }
      }
    }

    "should fail creating a new user if the email already exists" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(Roman).attempt // IO[Either[Throwable, String]]
        } yield userId

        program.asserting { outcome =>
          inside(outcome) {
            case Left(e) => e shouldBe a[PSQLException]
            case _       => fail()
          }
        }
      }
    }

    "should return None when updating a user that does not exist" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          maybeUser <- users.update(NewUser)
        } yield maybeUser

        program.asserting(_ shouldBe None)
      }
    }

    "should update an existing user" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          maybeUser <- users.update(UpdatedKalyam)
        } yield maybeUser

        program.asserting(_ shouldBe Some(UpdatedKalyam))
      }
    }

    "should delete a user" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete("srs@yandex.ru")
          maybeUser <- sql"""SELECT * FROM users WHERE email = 'srs@yandex.ru'"""
            .query[User]
            .option
            .transact(xa)
        } yield (result, maybeUser)

        program.asserting { case (result, maybeUser) =>
          result shouldBe true
          maybeUser shouldBe None
        }
      }
    }

    "should not delete a user that does not exist" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete("nobody@yandex.ru")
        } yield result

        program.asserting(_ shouldBe false)
      }
    }
  }
}
