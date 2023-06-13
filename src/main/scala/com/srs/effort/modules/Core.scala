package com.srs.effort.modules

import cats.*
import cats.implicits.*
import cats.effect.*
import doobie.util.transactor.Transactor

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.srs.effort.core.*
import com.srs.effort.config.*

final class Core[F[_]] private (val users: Users[F], val auth: Auth[F])

// postgres -> core -> app
object Core {
  def apply[F[_]: Async: Logger](
      xa: Transactor[F],
      tokenConfig: TokenConfig,
      emailServiceConfig: EmailServiceConfig
  ): Resource[F, Core[F]] = {
    val coreF = for {
      users  <- LiveUsers[F](xa)
      tokens <- LiveTokens[F](users)(xa, tokenConfig)
      emails <- LiveEmails[F](emailServiceConfig)
      auth   <- LiveAuth[F](users, tokens, emails)
    } yield new Core(users, auth)

    Resource.eval(coreF)
  }
}
