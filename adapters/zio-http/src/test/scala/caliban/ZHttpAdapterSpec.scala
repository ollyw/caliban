package caliban

import caliban.interop.tapir.TestData.sampleCharacters
import caliban.interop.tapir.{ FakeAuthorizationInterceptor, TapirAdapterSpec, TestApi, TestService }
import caliban.uploads.Uploads
import sttp.client3.UriContext
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.test.{ DefaultRunnableSpec, TestFailure, ZSpec }

import scala.language.postfixOps

object ZHttpAdapterSpec extends DefaultRunnableSpec {

  val apiLayer: ZLayer[zio.ZEnv, Throwable, Unit] =
    (for {
      interpreter <- TestApi.api.interpreter.toManaged
      _           <- Server
                       .start(
                         8088,
                         Http.route[Request] {
                           case _ -> !! / "api" / "graphql" =>
                             ZHttpAdapter.makeHttpService(
                               interpreter,
                               requestInterceptor = FakeAuthorizationInterceptor.bearer
                             )
                           case _ -> !! / "ws" / "graphql"  => ZHttpAdapter.makeWebSocketService(interpreter)
                         }
                       )
                       .forkManaged
      _           <- Clock.sleep(3 seconds).toManaged
    } yield ())
      .provideCustomLayer(TestService.make(sampleCharacters) ++ Uploads.empty +!+ Clock.live)
      .toLayer

  def spec: ZSpec[ZEnv, Any] = {
    val suite: ZSpec[Unit, Throwable] =
      TapirAdapterSpec.makeSuite(
        "ZHttpAdapterSpec",
        uri"http://localhost:8088/api/graphql",
        wsUri = Some(uri"ws://localhost:8088/ws/graphql")
      )
    suite.provideSomeLayerShared[ZEnv](apiLayer.mapError(TestFailure.fail))
  }
}
