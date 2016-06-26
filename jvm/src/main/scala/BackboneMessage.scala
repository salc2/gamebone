package com.chucho.server

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.scaladsl.{Sink, Source}
import scredis.{PubSubMessage, Redis}

import scala.annotation.tailrec
import scala.collection.immutable.Vector

/**
  * Created by chucho on 6/25/16.
  */
object BackboneMessage{
  type Channel = String
  def createReceiver(channel:Channel)(implicit materializer:ActorMaterializer) :Sink[Message,_] =
    Sink.foreach[Message]( m => m.asInstanceOf[TextMessage].textStream
      .runWith(Sink.foreach[String]{ msg =>
        Redis().publish[String](channel,msg)
      }) )

  def createPublisher(channel:Channel):Source[Message,_] = {
    Source.actorPublisher[TextMessage](Props(classOf[BackbonePublisher],Redis(),channel))
  }
}


class BackbonePublisher(private val redis:Redis, channel:String)
  extends ActorPublisher[TextMessage] with ActorLogging{


  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    log.info(s" starting.... -> ${self.path.name}")
  }

  val MaxBufferSize = 100
  var buf = Vector.empty[TextMessage]

  redis.subscriber.subscribe(channel) {
    case message@PubSubMessage.Message(channel, messageBytes) => {
      val msg = TextMessage(message.readAs[String]())
      if (buf.isEmpty && totalDemand > 0)
        onNext(msg)
      else {
        buf :+= msg
        deliverBuf()
      }
    }
  }

  override def receive: Receive = {
    case Request(_) => deliverBuf()
    case Cancel => context.stop(self)
  }


  @tailrec final def deliverBuf(): Unit =
    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliverBuf()
      }
    }
}
