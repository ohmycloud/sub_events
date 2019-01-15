package com.gac.xs6.bigdata.model

/**
  *
  * @param vin 车架号
  * @param ts 事件发生时间
  * @param mileage 当前里程数
  * @param alm_common_temp_diff 温度差异报警
  * @param alm_common_temp_high 电池高温报警
  * @param alm_common_esd_high 车载储能装置类型过压报警
  */
case class SourceData (
   vin:        String,
   ts:         Long,
   veh_odo:    Double,
   alm_common_temp_diff: Integer,
   alm_common_temp_high: Integer,
   alm_common_esd_high: Integer
)

