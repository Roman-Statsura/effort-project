package com.srs.effort.playground

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.Message
import javax.mail.Transport

import cats.effect.IOApp
import cats.effect.IO

import com.srs.effort.core.*
import com.srs.effort.config.*

object EmailsPlayground {
  def main(args: Array[String]): Unit = {
    // configs
    val host        = "smtp.ethereal.email"
    val port        = 587
    val user        = "orville11@ethereal.email"
    val pass        = "kTM8hqrX4C6XvtD9Zp"
    val frontendUrl = "https://google.com"

    val token = "ABCD1234"

    // properties file
    val prop = new Properties
    prop.put("mail.smtp.auth", true)
    prop.put("mail.smtp.starttls.enable", true)
    prop.put("mail.smtp.host", host)
    prop.put("mail.smtp.port", port)
    prop.put("mail.smtp.ssl.trust", host)

    // authentication
    val auth = new Authenticator {
      override protected def getPasswordAuthentication(): PasswordAuthentication =
        new PasswordAuthentication(user, pass)
    }

    // session
    val session = Session.getInstance(prop, auth)

    // email itself
    val subject = "Email form unknown"
    val content = s"""
        <div style="
        border: 1px solid black;
        padding: 20px;
        font-family: sans-serif;
        line-height: 2;
        font-size: 20px;
        ">
        <h1>Effort: Password Recovery</h1>
        <p>Your password recovery token is: $token</p>
        <p> 
          Click <a href="$frontendUrl/login">here</a> to get back to the application
        </p>
        <p> Hello from Effort-service</p>
        </div>  
      """

    // message
    val message = new MimeMessage(session)
    message.setFrom("srs@yandex.ru")
    message.setRecipients(Message.RecipientType.TO, "the.user@gmail.com")
    message.setSubject(subject)
    message.setContent(content, "text/html; charset=utf-8")

    // send
    Transport.send(message)

  }
}

object EmailsEffectPlayground extends IOApp.Simple {
  override def run: IO[Unit] = for {
    emails <- LiveEmails[IO](
      EmailServiceConfig(
        host = "smtp.ethereal.email",
        port = 587,
        user = "orville11@ethereal.email",
        pass = "kTM8hqrX4C6XvtD9Zp",
        frontendUrl = "https://google.com"
      )
    )
    _ <- emails.sendPasswordRecoveryEmail("someone@srs.com", "SRSTOKEN")
  } yield ()
}
