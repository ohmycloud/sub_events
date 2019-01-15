package com.gac.xs6.bigdata.core.impl

import com.datastax.spark.connector.util.Logging
import com.gac.xs6.bigdata.core.EventsExact
import com.gac.xs6.bigdata.model.{Event, SourceData}
import org.apache.spark.streaming.dstream.DStream
import scala.collection.mutable

object EventsExactImpl extends EventsExact with Logging {
  override def extract(stream: DStream[Option[(String, SourceData)]]): DStream[(String, Event)] = {
    val eventMaps: mutable.HashMap[String,Integer] = scala.collection.mutable.HashMap() // 存放事件的 Map
    synchronized {
      stream.map { result =>
        val c = result.getOrElse(null)
        if (null != c && null != c._2) {
          try {
            val data: SourceData = c._2
            val ts: Long = data.ts
            val vin: String = data.vin
            val veh_odo: Double = data.veh_odo

            eventMaps.clear()

            if (ts != null && ts > 956678797000L && ts / 1000 / 10000 / 10000 / 10 < 10 && vin != null) { // 过滤异常的信号
              if (null != data.alm_common_temp_diff && 1==data.alm_common_temp_diff) eventMaps.put("alm_common_temp_diff", data.alm_common_temp_diff) // 温度差异报警
              if (null != data.alm_common_temp_high && 1==data.alm_common_temp_high) eventMaps.put("alm_common_temp_high", data.alm_common_temp_high) // 电池高温报警
              if (null != data.alm_common_esd_high  && 1==data.alm_common_esd_high) eventMaps.put("alm_common_esd_high", data.alm_common_esd_high)
            }

            if (eventMaps.size > 0 ) {
              (c._1, Event(vin, ts, veh_odo, eventMaps))
            } else {
              null
            }
          } catch {
            case e: Exception => println(e)
            null
          }
        } else {
          null
        }
      }
    }
  }
}
