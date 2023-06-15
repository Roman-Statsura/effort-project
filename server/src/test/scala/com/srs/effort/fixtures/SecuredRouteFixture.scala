package com.srs.effort.fixtures

import cats.data.*
import cats.effect.*
import org.http4s.*
import org.http4s.headers.*
import tsec.mac.jca.HMACSHA256
import tsec.authentication.IdentityStore
import tsec.authentication.JWTAuthenticator
import tsec.jws.mac.JWTMac

import scala.concurrent.duration.*

import com.srs.effort.domain.user.*
import com.srs.effort.domain.security.*
import tsec.authentication.SecuredRequestHandler

trait SecuredRouteFixture extends UserFixture {
  val mockedAuthenticator: Authenticator[IO] = {
    val key = HMACSHA256.unsafeGenerateKey
    val idStore: IdentityStore[IO, String, User] = (email: String) =>
      if (email == romanEmail) OptionT.pure(Roman)
      else if (email == kalyamEmail) OptionT.pure(Kalyam)
      else OptionT.none[IO, User]

    JWTAuthenticator.unbacked.inBearerToken(
      1.day,   // expiration of tokens
      None,    // max idle time
      idStore, // identity store
      key      // hash key
    )
  }

  extension (r: Request[IO])
    def withBearerToken(a: JwtToken): Request[IO] =
      r.putHeaders {
        val jwtString = JWTMac.toEncodedString[IO, HMACSHA256](a.jwt)
        Authorization(Credentials.Token(AuthScheme.Bearer, jwtString))
      }

  given securedHandler: SecuredHandler[IO] = SecuredRequestHandler(mockedAuthenticator)
}
