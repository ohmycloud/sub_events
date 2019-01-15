package com.gac.xs6.bigdata.pipeline

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream

trait DStreamSource[M] {
  def stream(ssc: StreamingContext): DStream[M]
}

trait RDDSource[M] {
  def rdd(sc: SparkContext): RDD[M]
}

