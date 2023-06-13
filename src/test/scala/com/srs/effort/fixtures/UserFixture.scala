package com.srs.effort.fixtures

import cats.effect.IO
import com.srs.effort.core.*
import com.srs.effort.domain.auth.*
import com.srs.effort.domain.user.*

import com.srs.effort.domain.auth

trait UserFixture {

  val mockedUsers: Users[IO] = new Users[IO] {
    override def find(email: String): IO[Option[User]] =
      if (email == romanEmail) IO.pure(Some(Roman))
      else IO.pure(None)
    override def create(user: User): IO[String]       = IO.pure(user.email)
    override def update(user: User): IO[Option[User]] = IO.pure(Some(user))
    override def delete(email: String): IO[Boolean]   = IO.pure(true)
  }

  /*
roman123 - $2a$10$OP8t8F306pjaLbrwXQEBHujRQ01vgBHQBdQ3Z.4aNl9QEYRLZGkdi
kalyam - $2a$10$4c3Sak/49QKXcdCrRYOMhOdWVgdpj9Tr5iy2xiymPS8dHfU/kJGc6
simplepassword - $2a$10$x9eUIPdSBV24m8yrzpjAbuhcvb8hma6b7Y.Xxc6eeqlG68JU3LOjC
kalyamJopin - $2a$10$6T/n/pZgatFQ1MAUK79hr.PGqOXlchG3uyU1buGnuuDGUWCLatmwW
   */
  val Roman = User(
    "srs@yandex.ru",
    "$2a$10$OP8t8F306pjaLbrwXQEBHujRQ01vgBHQBdQ3Z.4aNl9QEYRLZGkdi",
    Some("Roman"),
    Some("Statsura"),
    Some("Unknown"),
    Role.ADMIN
  )
  val romanEmail    = Roman.email
  val romanPassword = "roman123"

  val Kalyam = User(
    "kalyam@yandex.ru",
    "$2a$10$4c3Sak/49QKXcdCrRYOMhOdWVgdpj9Tr5iy2xiymPS8dHfU/kJGc6",
    Some("Kalyam"),
    Some("Jopin"),
    Some("Yandex"),
    Role.RECRUITER
  )
  val kalyamEmail    = Kalyam.email
  val kalyamPassword = "kalyam"

  val NewUser = User(
    "newuser@gmail.com",
    "$2a$10$ozAyjeeo2kLb5Pw.W1ahROQWzDuhtCnrDknUPRh9kO13xmxem1Osa",
    Some("John"),
    Some("Doe"),
    Some("Some company"),
    Role.RECRUITER
  )

  val UpdatedKalyam = User(
    "kalyam@yandex.ru",
    "$2a$10$z4MhJ9MQjCJTTODH8.NBouEpVFoMufPKQs/sKc568Sqeu5oiLlaLq",
    Some("KALYAM"),
    Some("JOPIN"),
    Some("Mail"),
    Role.RECRUITER
  )

  val NewUserRoman = NewUserInfo(
    romanEmail,
    romanPassword,
    Some("Daniel"),
    Some("Ciocirlan"),
    Some("Rock the JVM")
  )

  val NewUserKalyam = NewUserInfo(
    kalyamEmail,
    kalyamPassword,
    Some("Kalyam"),
    Some("Jopin"),
    Some("Yandex")
  )
}
