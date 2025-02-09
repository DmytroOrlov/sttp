package com.softwaremill.sttp.asynchttpclient.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.impl.monix.TaskMonadAsyncError
import com.softwaremill.sttp._
import com.softwaremill.sttp.internal._
import io.netty.buffer.{ByteBuf, Unpooled}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher

class AsyncHttpClientMonixBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
    implicit scheduler: Scheduler
) extends AsyncHttpClientBackend[Task, Observable[ByteBuffer]](asyncHttpClient, TaskMonadAsyncError, closeClient) {

  override def send[T](r: Request[T, Observable[ByteBuffer]]): Task[Response[T]] = {
    super.send(r).guarantee(Task.shift)
  }

  override protected def streamBodyToPublisher(s: Observable[ByteBuffer]): Publisher[ByteBuf] =
    s.map(Unpooled.wrappedBuffer).toReactivePublisher

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Observable[ByteBuffer] =
    Observable.fromReactivePublisher(p)

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] = {

    val bytes = Observable
      .fromReactivePublisher(p)
      .foldLeftL(ByteBuffer.allocate(0))(concatByteBuffers)

    bytes.map(_.array())
  }
}

object AsyncHttpClientMonixBackend {

  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit scheduler: Scheduler
  ): SttpBackend[Task, Observable[ByteBuffer]] =
    new FollowRedirectsBackend(new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient))

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfig(
      cfg: AsyncHttpClientConfig
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(new DefaultAsyncHttpClient(cfg), closeClient = true)

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true
    )

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingClient(
      client: AsyncHttpClient
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(client, closeClient = false)
}
