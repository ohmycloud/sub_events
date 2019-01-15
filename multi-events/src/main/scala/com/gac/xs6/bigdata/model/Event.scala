package com.gac.xs6.bigdata.model

import scala.collection.mutable

case class Event (
                   var vin: String                          = "",   // 车架号
                   var ts: Long                             = -999, // 发生时间
                   var veh_odo: Double                      = -999, // 当前里程数
                   val eventMaps: mutable.HashMap[String, Integer] = scala.collection.mutable.HashMap()
                 )

object Event {}