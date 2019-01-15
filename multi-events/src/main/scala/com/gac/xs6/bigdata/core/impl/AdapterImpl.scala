package com.gac.xs6.bigdata.core.impl

import com.alibaba.fastjson.JSON
import com.datastax.spark.connector.util.Logging
import com.gac.xs6.bigdata.core.Adapter
import com.gac.xs6.bigdata.model.SourceData
import org.apache.spark.streaming.dstream.DStream


object AdapterImpl extends Adapter with Logging {

  override def extract(sourceDstream: DStream[String]): DStream[Option[(String,SourceData)]] = {
    sourceDstream.map { r =>
      val data = JSON.parseObject(r)

      val vin = data.getString("vin")
      val ts = data.getLong("ts")
      val veh_odo = data.getDouble("veh_odo")
      val alm_common_temp_diff: Integer = data.getInteger("alm_common_temp_diff")
      val alm_common_temp_high: Integer = data.getInteger("alm_common_temp_high")
      val alm_common_esd_high: Integer = data.getInteger("alm_common_esd_high")

      if (null!=vin) {
        Some((vin,SourceData(vin, ts, veh_odo,alm_common_temp_diff,alm_common_temp_high,alm_common_esd_high)))
      } else {
        null
      }
    }
  }
}
