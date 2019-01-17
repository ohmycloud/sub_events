package com.gac.xs6.bigdata.core

import com.gac.xs6.bigdata.model.{Event, EventUpdate, SourceData}
import org.apache.spark.streaming.dstream.DStream
import scala.collection.mutable.ArrayBuffer

trait Adapter {
  def extract(sourceDstream:DStream[String]):DStream[Option[(String,SourceData)]]
}

trait EventsExact {
  /**
    * 事件提取
    * @param stream
    * @return
    */
  def extract(stream: DStream[Option[(String, SourceData)]]): DStream[(String, Event)]
}

/**
  * 事件检测
  */
trait EventsCheck {
  def extract(stream: DStream[(String, Event)]): DStream[(String,  ArrayBuffer[EventUpdate])]
}
