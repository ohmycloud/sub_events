package com.gac.xs6.bigdata.module

import com.datastax.spark.connector.util.Logging
import com.gac.xs6.bigdata.EventsApplication
import com.gac.xs6.bigdata.conf.{SocketConfiguration, SparkConfiguration}
import com.gac.xs6.bigdata.core.impl.{AdapterImpl, EventsCheckImpl, EventsExactImpl}
import com.gac.xs6.bigdata.core.{Adapter, EventsCheck, EventsExact}
import com.gac.xs6.bigdata.pipeline.DStreamSource
import com.google.inject.{AbstractModule, Provides, Singleton}
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.{SparkConf, SparkContext}
import org.json4s.DefaultFormats
import com.gac.xs6.bigdata.util.Json4sHelper._

object MainModule extends AbstractModule with Logging {

  override def configure(): Unit = {
    logInfo("starting main injection module")
    bind(classOf[SparkConfiguration]).asEagerSingleton()

    bind(classOf[EventsApplication])
    bind(classOf[Adapter]).toInstance(AdapterImpl)
    bind(classOf[EventsExact]).toInstance(EventsExactImpl)
    bind(classOf[EventsCheck]).toInstance(EventsCheckImpl)
  }

  @Provides
  @Singleton
  def sparkContext(sparkConf: SparkConfiguration): SparkContext = {
    SparkContext.getOrCreate(new SparkConf(true)
      .setAppName("socketStreaming")
      .setMaster(sparkConf.sparkMaster)
      .set("spark.cleaner.ttl", sparkConf.ttl) //显示生命定期清理RDD和checkpoint
      .set("spark.cleaner.referenceTracking.cleanCheckpoints", sparkConf.cleanCheckpoints) //显示生命定期清理RDD和checkpoint
      .set("spark.ui.enabled", sparkConf.sparkUIEnabled.toString)
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    )
  }

  @Provides
  @Singleton
  def SocketDataSource(socketConfiguration: SocketConfiguration): DStreamSource[String] = {
    new DStreamSource[String] {
      def stream(ssc: StreamingContext): DStream[String] = {
        implicit val formats = DefaultFormats + StringToBigDecimal + StringToInt + StringToDouble + StringToInstant + StringToLong

        val stream: DStream[String] = ssc.socketTextStream(socketConfiguration.host, socketConfiguration.port)
        stream
      }
    }
  }
}
