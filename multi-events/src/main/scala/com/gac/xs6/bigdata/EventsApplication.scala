package com.gac.xs6.bigdata

import javax.inject.{Inject, Singleton}
import com.datastax.spark.connector.util.Logging
import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import com.gac.xs6.bigdata.EventsApplication.Params
import com.gac.xs6.bigdata.conf.SparkConfiguration
import com.gac.xs6.bigdata.core.{Adapter, EventsCheck, EventsExact}
import com.gac.xs6.bigdata.model.{Event, EventUpdate, SourceData}
import com.gac.xs6.bigdata.module.MainModule
import com.gac.xs6.bigdata.pipeline.DStreamSource
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Seconds, StreamingContext}
import scopt.OptionParser
import org.apache.spark.SparkContext

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object EventsApplication extends App with Logging {
  logInfo("socket streaming started")

  val parser = new OptionParser[Params]("EventsApplication") {
    head("socket streaming")

    opt[String]('c', "com/xs/telematics/conf")
      .text("config.resource for gac")
      .action((x, c) => c.copy(conf = x))

    help("help").text("prints this usage text")
  }

  parser.parse(args, Params()) match {
    case Some(params) =>
      val injector = Guice.createInjector(MainModule)
      val runner = injector.getInstance(classOf[EventsApplication])
      ConfigFactory.invalidateCaches()
      runner.run(params)
    case _ => sys.exit(1)
  }

  case class Params(conf: String = "application.conf")
}

@Singleton
class EventsApplication @Inject() (
                                  sparkConf: SparkConfiguration,
                                  sparkContext: SparkContext,
                                  source: DStreamSource[String],
                                  adapter: Adapter,
                                  eventsExact: EventsExact,
                                  eventsCheck: EventsCheck
                                  ) extends Serializable with Logging {
  private def createNewStreamingContext: StreamingContext = {
    val ssc = new StreamingContext(sparkContext = sparkContext, Seconds(1))
    ssc.checkpoint(sparkConf.checkPointPath)

    sys.addShutdownHook {
      logInfo("Gracefully stopping Spark Streaming Application")
      ssc.stop(stopSparkContext = true, stopGracefully = true)
      logInfo("Application stopped")
    }

    val dStream: DStream[String] = source.stream(ssc)
    val sourceDstream: DStream[Option[(String,SourceData)]] = adapter.extract(dStream) // sourceData 源数据

    val eventsExactStream: DStream[(String,Event)] = eventsExact.extract(sourceDstream)

    val eventsCheckStream: DStream[(String,  ArrayBuffer[EventUpdate])] = eventsCheck.extract(eventsExactStream)

    val result = eventsCheckStream.filter(x => null != x).filter( y => None != y).filter( z => z._2.size > 0)

    try {
      result.foreachRDD(rdd => {
        if (rdd.count() > 0) {
          for (row <- rdd.collect()) {
            if (null != row) {
              println(row)
            }
          }
        }
      })
    } catch {
      case e:Exception => println(e)
    }


    //eventsCheckStream.print()

    ssc
  }

  def run(params: Params): Unit = {

    val ssc = StreamingContext.getOrCreate(sparkConf.checkPointPath, () => createNewStreamingContext)

    ssc.start()
    ssc.awaitTermination()
  }
}