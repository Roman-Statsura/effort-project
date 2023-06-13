package com.srs.effort.modules

import org.http4s.*
import cats.effect.*
import cats.data.OptionT
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import cats.*
import cats.implicits.*
import org.typelevel.log4cats.Logger

import tsec.authentication.AugmentedJWT
import tsec.authentication.JWTAuthenticator
import tsec.authentication.BackingStore
import tsec.authentication.IdentityStore
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt
import tsec.passwordhashers.PasswordHash
import tsec.common.SecureRandomId

import com.srs.effort.config.*
import com.srs.effort.http.routes.*
import com.srs.effort.domain.security.*
import com.srs.effort.domain.user.*
import com.srs.effort.core.*
import tsec.authentication.SecuredRequestHandler

class HttpApi[F[_]: Concurrent: Logger] private (core: Core[F], authenticator: Authenticator[F]) {
  given requestHandler: SecuredHandler[F] = SecuredRequestHandler(authenticator)
  private val healthRoutes                = HealthRoutes[F].routes
  private val authRoutes                  = AuthRoutes[F](core.auth, authenticator).routes

  val endpoints = Router(
    "/api" -> (healthRoutes <+> authRoutes)
  )
}

object HttpApi {

  def createAuthenticator[F[_]: Sync](
      users: Users[F],
      securityConfig: SecurityConfig
  ): F[Authenticator[F]] = {
    // 1. Identity store: String => OptionT[F, User]
    val idStore: IdentityStore[F, String, User] = (email: String) => OptionT(users.find(email))

    // 2. backing store for JWT tokens: BackingStore[F, id, JwtToken]
    val tokenStoreF = Ref.of[F, Map[SecureRandomId, JwtToken]](Map.empty).map { ref =>
      new BackingStore[F, SecureRandomId, JwtToken] {
        // mutable map - race conditions
        // ref
        override def get(id: SecureRandomId): OptionT[F, JwtToken] =
          OptionT( /*F[JwtToken]*/ ref.get.map(_.get(id)))

        override def put(elem: JwtToken): F[JwtToken] =
          ref.modify(store => (store + (elem.id -> elem), elem))

        override def update(v: JwtToken): F[JwtToken] =
          put(v)

        override def delete(id: SecureRandomId): F[Unit] =
          ref.modify(store => (store - id, ()))
      }
    }

    // 3. hashing key
    val keyF = HMACSHA256.buildKey[F](securityConfig.secret.getBytes("UTF-8"))

    for {
      key        <- keyF
      tokenStore <- tokenStoreF
      // 4. authenticator
    } yield JWTAuthenticator.backed.inBearerToken(
      expiryDuration = securityConfig.jwtExpiryDuration, // expiration of tokens
      maxIdle = None,                                    // max idle time
      identityStore = idStore,                           // identity store
      tokenStore = tokenStore,
      signingKey = key // hash key
    )
  }

  def apply[F[_]: Async: Logger](
      core: Core[F],
      securityConfig: SecurityConfig
  ): Resource[F, HttpApi[F]] =
    Resource
      .eval(createAuthenticator(core.users, securityConfig))
      .map(authenticator => new HttpApi[F](core, authenticator))
}
