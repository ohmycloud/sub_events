package com.gac.xs6.bigdata.model

// 国标事件
case class EventUpdate(
                        var vin: String           = "",   // 车架号
                        var eventName: String     = "",   // 事件名称
                        var eventStartTime: Long  = -999, // 事件开始时间
                        var eventEndTime: Long    = -999, // 事件结束时间
                        var eventStatus: Int      = -999, // 事件状态
                        var eventType: Int        = -999, // 事件类型
                        var startMileage: Double  = -999  // 事件发生时的里程数

                      ) {
  def eventDuration: Long = eventEndTime - eventStartTime  // 事件持续时长
}

object EventUpdate {}