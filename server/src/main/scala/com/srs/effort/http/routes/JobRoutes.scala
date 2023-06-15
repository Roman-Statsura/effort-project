package com.srs.effort.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import scala.language.implicitConversions

import org.http4s.*
import cats.effect.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import cats.*
import cats.implicits.*
import org.typelevel.log4cats.Logger

import tsec.authentication.asAuthed
import tsec.authentication.{SecuredRequestHandler, TSecAuthService}

import java.util.UUID
import scala.collection.mutable
import com.srs.effort.core.*
import com.srs.effort.domain.job.*
import com.srs.effort.domain.user.*
import com.srs.effort.domain.security.*
import com.srs.effort.domain.pagination.*
import com.srs.effort.http.validation.syntax.*
import com.srs.effort.logging.syntax.*
import com.srs.effort.http.responses.*
import fs2.io.Watcher.EventType.Created

class JobRoutes[F[_]: Concurrent: Logger: SecuredHandler] private (jobs: Jobs[F])
    extends HttpValidationDsl[F] {

  object OffsetQueryParams extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object LimitQueryParams  extends OptionalQueryParamDecoderMatcher[Int]("limit")

  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root :? LimitQueryParams(limit) +& OffsetQueryParams(offset) =>
      for {
        filter   <- req.as[JobFilter]
        jobsList <- jobs.all(filter, Pagination(limit, offset))
        resp     <- Ok(jobsList)
      } yield resp
  }

  private val findJobRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / UUIDVar(id) =>
    jobs.find(id) flatMap {
      case Some(job) => Ok(job)
      case None      => NotFound(FailureResponse(s"Job $id not found"))
    }
  }

  private val createJobRoute: AuthRoute[F] = { case req @ POST -> Root / "create" asAuthed _ =>
    req.request.validate[JobInfo] { jobInfo =>
      for {
        jobId <- jobs.create("srs1012@mail.ru", jobInfo)
        resp  <- Created(jobId)
      } yield resp
    }
  }

  private val updateJobRoute: AuthRoute[F] = { case req @ PUT -> Root / UUIDVar(id) asAuthed user =>
    req.request.validate[JobInfo] { jobInfo =>
      jobs.find(id) flatMap {
        case None =>
          NotFound(FailureResponse(s"Cannot delete job $id: not found"))
        case Some(job) if user.owns(job) || user.isAdmin =>
          jobs.update(id, jobInfo) *> Ok()
        case _ =>
          Forbidden(FailureResponse("You can only delete your own jobs"))
      }
    }
  }

  private val deleteJobRoute: AuthRoute[F] = {
    case req @ DELETE -> Root / UUIDVar(id) asAuthed user =>
      jobs.find(id) flatMap {
        case None =>
          NotFound(FailureResponse(s"Cannot delete job $id: not found"))
        case Some(job) if user.owns(job) || user.isAdmin =>
          jobs.delete(id) *> Ok()
        case _ =>
          Forbidden(FailureResponse("You can only delete your own jobs"))
      }
  }

  val unauthedRoutes = allJobsRoute <+> findJobRoutes
  val authedRoutes = SecuredHandler[F].liftService(
    createJobRoute.restrictedTo(allRoles) |+|
      updateJobRoute.restrictedTo(allRoles) |+|
      deleteJobRoute.restrictedTo(allRoles)
  )
  val routes = Router(
    "/jobs" -> (unauthedRoutes <+> authedRoutes)
  )
}

object JobRoutes {
  def apply[F[_]: Concurrent: Logger: SecuredHandler](jobs: Jobs[F]) =
    new JobRoutes[F](jobs)
}
