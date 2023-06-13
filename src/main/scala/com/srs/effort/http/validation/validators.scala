package com.srs.effort.http.validation

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.implicits.*
import java.net.URL
import scala.util.{Try, Success, Failure}

import com.srs.effort.domain.auth.*
import com.srs.effort.domain.user.*

object validators {

  sealed trait ValidationFailure(val errorMessage: String)
  case class EmptyField(fieldName: String) extends ValidationFailure(s"'$fieldName' is empty")
  case class InvalidUrl(fieldName: String)
      extends ValidationFailure(s"'$fieldName' is not valid URL")
  case class InvalidEmail(fieldName: String)
      extends ValidationFailure(s"'$fieldName' is not valid email")

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  def validateRequired[A](field: A, fieldName: String)(
      required: A => Boolean
  ): ValidationResult[A] =
    if (required(field)) field.validNel
    else EmptyField(fieldName).invalidNel

  def validateUrl(field: String, fieldName: String): ValidationResult[String] =
    Try(URL(field).toURI()) match {
      case Success(_) => field.validNel
      case Failure(e) => InvalidUrl(fieldName).invalidNel
    }

  val emailRegex =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def validateEmail(field: String, fieldName: String): ValidationResult[String] =
    if (emailRegex.findFirstMatchIn(field).isDefined) field.validNel
    else InvalidEmail(fieldName).invalidNel

  given loginInfoValidator: Validator[LoginInfo] = (loginInfo: LoginInfo) => {
    val validUserEmail = validateRequired(loginInfo.email, "email")(_.nonEmpty)
      .andThen(e => validateEmail(e, "email"))
    val validUserPassword = validateRequired(loginInfo.password, "password")(_.nonEmpty)

    (validUserEmail, validUserPassword).mapN(LoginInfo.apply)
  }

  given newUserInfoValidater: Validator[NewUserInfo] = (newUserInfo: NewUserInfo) => {
    val validUserEmail = validateRequired(newUserInfo.email, "email")(_.nonEmpty)
      .andThen(e => validateEmail(e, "email"))
    val validUserPassword = validateRequired(newUserInfo.password, "password")(_.nonEmpty)

    (
      validUserEmail,
      validUserPassword,
      newUserInfo.firstName.validNel,
      newUserInfo.lastName.validNel,
      newUserInfo.company.validNel
    ).mapN(NewUserInfo.apply)
  }

  given newPasswordInfoValidator: Validator[NewPasswordInfo] = (newPasswordInfo: NewPasswordInfo) =>
    {
      val validOldPassword =
        validateRequired(newPasswordInfo.oldPassword, "old password")(_.nonEmpty)
      val validNewPassword =
        validateRequired(newPasswordInfo.newPassword, "new password")(_.nonEmpty)

      (validOldPassword, validNewPassword).mapN(NewPasswordInfo.apply)
    }
}
