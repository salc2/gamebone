package com.chucho.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn


object WebServer {
  def main(args:Array[String]):Unit = {
    implicit val system = ActorSystem("gamebone-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val route =
      path("api" / Segment / "gamebone.js") { tokenRest =>
          val loader = getClass.getClassLoader
          val jsmain = loader.getResource("main.js")
          getFromFile(jsmain.getFile)
      } ~
      path(Remaining){ remain =>
        pathEnd{
          getFromResource(s"http/$remain/index.html")
        }~
        getFromResource(s"http/$remain")
      }



    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
