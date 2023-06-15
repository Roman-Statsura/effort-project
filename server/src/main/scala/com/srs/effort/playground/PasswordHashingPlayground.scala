package com.srs.effort.playground

import cats.effect.IOApp
import cats.effect.IO
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash

object PasswordHashingPlayground extends IOApp.Simple {
  override def run: IO[Unit] =
    BCrypt.hashpw[IO]("roman123").flatMap(IO.println) *>
      BCrypt.hashpw[IO]("kalyam").flatMap(IO.println) *>
      BCrypt.hashpw[IO]("simplepassword").flatMap(IO.println) *>
      BCrypt.hashpw[IO]("kalyamJopin").flatMap(IO.println)
}
