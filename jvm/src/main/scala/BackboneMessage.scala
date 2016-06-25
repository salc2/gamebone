package com.chucho.server

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{TextMessage}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.scaladsl.{Sink, Source}
import scredis.{PubSubMessage, Redis, RedisConfig}

import scala.annotation.tailrec
import scala.collection.immutable.Vector

/**
  * Created by chucho on 6/25/16.
  */
class BackboneMessage(private val redis:Redis) {
  type Channel = String
  def createReceiver(channel:Channel):Sink[TextMessage,Any] =
    Sink.foreach[TextMessage]( m => redis.publish[TextMessage](channel,m) )

  def createPublisher(channel:Channel):Source[TextMessage,_] = {
    Source.actorPublisher[TextMessage](Props(classOf[BackbonePublisher],redis,channel))
  }
}

object BackboneMessage{
  def apply(implicit system:ActorSystem): BackboneMessage = new BackboneMessage(Redis())
  def apply(redisConfg:RedisConfig)(implicit system:ActorSystem): BackboneMessage =
    new BackboneMessage(Redis(redisConfg))
}

class BackbonePublisher(private val redis:Redis, channel:String)
  extends ActorPublisher[TextMessage] with ActorLogging{

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