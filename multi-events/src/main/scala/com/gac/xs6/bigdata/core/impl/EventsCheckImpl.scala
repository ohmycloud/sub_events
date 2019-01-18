package com.gac.xs6.bigdata.core.impl

import com.datastax.spark.connector.util.Logging
import com.gac.xs6.bigdata.core.EventsCheck
import com.gac.xs6.bigdata.model.{Event, EventUpdate}
import org.apache.spark.streaming.{Duration, State, StateSpec}
import org.apache.spark.streaming.dstream.DStream
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * 事件检测
  */
object EventsCheckImpl extends EventsCheck with Logging {

  val eventIdleTimeout: Duration = Duration(15 * 60 * 1000) // 超时时间设置为 15 分钟
  val eventDuration = Duration( 15 * 1000) // 俩个 event 之间的时间之差为 15秒（结束这个事件）, 生产环境为 30s

  /**
    * 将每辆车的事件流转为事件状态流
    * @param stream (vin, Event) 事件流
    * @return 事件状态流,(vin, ArrayBuffer[EventUpdate])
    */
  override def extract(stream: DStream[(String, Event)]): DStream[(String,  ArrayBuffer[EventUpdate])] = {
    stream.mapWithState(eventStateSpec)
      .flatMap(opt => opt)
  }

  val eventStateSpec = StateSpec.function(mappingEvent _).timeout(eventIdleTimeout)

  /**
    *
    * @param vin 车架号,
    * @param opt Event 事件流
    * @param state HashMap[eventName, EventUpdate]
    * @return (vin, ArrayBuffer[EventUpdate])
    */
  def mappingEvent(vin: String, opt: Option[Event], state: State[mutable.HashMap[String, EventUpdate]]): Option[(String, ArrayBuffer[EventUpdate])] = {
    // 事件名列表
    val eventsList = List(
      "alm_common_temp_diff",
      "alm_common_temp_high",
      "alm_common_esd_high",
      "alm_common_esd_low",
      "alm_common_soc_low",
      "alm_common_sc_high",
      "alm_common_sc_low",
      "alm_common_soc_high",
      "alm_common_soc_hop",
      "alm_common_esd_unmatch",
      "alm_common_sc_consistency",
      "alm_common_insulation",
      "alm_common_dcdc_temp",
      "alm_common_brk",
      "alm_common_dcdc_st",
      "alm_common_dmc_temp",
      "alm_common_hvil_st",
      "alm_common_dm_temp",
      "alm_common_esd_charge_over"
    )

    val arrayResult = new ArrayBuffer[EventUpdate]()  // 保存返回的 EventUpdate 数组

    if (state.isTimingOut() && state.exists()) {
      val lastState = state.get()

      val resultArray = new ArrayBuffer[EventUpdate]()

      lastState.map { ev =>
        val event = ev._2

        val updatedEvent = EventUpdate(
          vin             = event.vin,
          eventName       = event.eventName,
          eventStartTime  = event.eventStartTime,
          eventEndTime    = event.eventEndTime,
          eventStatus     = 2,
          eventType       = event.eventType,
          startMileage    = event.startMileage
        )

        resultArray.append(updatedEvent)

      }  // 超时的事件, 状态置为 2
      Some((vin, resultArray))                    // 发送到 downstream, 但不移除状态
    } else opt.flatMap( data => {

      val dataEvents = data.eventMaps             // data 中的 eventMaps
      val newTime    = data.ts                    // 新的数据的时间戳
      var lastEvents: mutable.HashMap[String,EventUpdate]  = getLastEvent(data, state, arrayResult) // 获取每个事件上一次的状态

      eventsList.map { topicEvent =>

        if (dataEvents.contains(topicEvent) &&
            lastEvents.contains(topicEvent)
        ) {
          // 判断事件结束，初始化或更新
          lastEvents.map { ev =>
            val eventName   = ev._1
            val eventUpdate = ev._2

            if ( (newTime - eventUpdate.eventEndTime) > eventDuration.milliseconds ) { // 事件正常结束

              eventUpdate.eventStatus = 0  // 将事件类型置为 0, 即结束上一段事件

              val eventStartTime = eventUpdate.eventStartTime
              val eventEndTime   = eventUpdate.eventEndTime
              val eventDuration  = eventUpdate.eventDuration

              println(f"划分了一个事件: " +
                f"事件名: $eventName, " +
                f"事件开始时间: $eventStartTime, " +
                f"事件结束时间：, $eventEndTime" +
                f"事件持续时长:  $eventDuration"
              )


              arrayResult.append(eventUpdate)                    // 将结束的事件放到结果数组中
              initState(data, eventName, state)                  // 初始化新的事件
              arrayResult.append(state.get().get(eventName).get) // 将新的事件放到结果数组中

            } else { // 事件正在进行
              lastEvents = getUpdatedEvent(data, eventName, lastEvents)
              state.update(lastEvents) // 用 newState 更新旧的状态
              None  // 在内存中更新事件, 不返回东西
            }
          }

        } else if (dataEvents.contains(topicEvent) &&
                  !lastEvents.contains(topicEvent)
        ) {

          initState(data, topicEvent, state) // 新建 state, 需要更新 state
          arrayResult.append(state.get().get(topicEvent).get)

        } else if (!dataEvents.contains(topicEvent) && // 该事件这次没有出现
                    lastEvents.contains(topicEvent)    // 该事件之前已经出现
        ) {
          // 更新事件的状态, 比如事件结束时间
          lastEvents.map { ev =>
            //val eventName   = ev._1
            //val updatedState = getUpdatedEvent(data, eventName, lastEvents)
            //state.update(updatedState)
            None  // 没有发生事件, 什么也不做
          }
        } else {
          None
        }
      }

      if (arrayResult.size > 0) {
        Some(vin, arrayResult)
      } else {
        None
      }

    })
  }

  /**
    * 事件正在进行, 则更新内存中的 state 对象, 不划分
    * @param data 数据源
    * @param eventName 事件名
    * @param lastEvents 上一个 event 的状态
    * @return HashMap[eventName,EventUpdate]
    */
  def getUpdatedEvent(data: Event, eventName: String, lastEvents: mutable.HashMap[String,EventUpdate]): mutable.HashMap[String,EventUpdate] = {

    val ts: Long = data.ts

    // 只有出现事件时才更新事件结束时间
    if (data.eventMaps.contains(eventName)) {
      lastEvents(eventName).eventEndTime = ts // 事件正在进行, 将上一个事件的事件结束时间置为新的数据源的 ts
      lastEvents(eventName).eventStatus  = 1  // 事件正在进行, 这这种类型的事件状态置为 1
    }

    lastEvents
  }

  /**
    * 获取每个事件的上一次的状态
    * @param data 事件的数据源
    * @param state HashMap[evName, EventUpdate]
    * @return HashMap[evName, EventUpdate]
    */
  def getLastEvent(data: Event, state: State[mutable.HashMap[String, EventUpdate]], result: ArrayBuffer[EventUpdate]): mutable.HashMap[String,EventUpdate] = {

    if (state.exists()) {
      val dataEvents = data.eventMaps // 获取 data 中的 events
      val lastState = state.get()

      dataEvents.map { ev =>
        val eventName = ev._1
        if (lastState.contains(eventName)) { // state 里面包含已经出现的事件, 什么也不做，最后原样取回
            None  // 什么也不做, 没有副作用
        } else {  // state 中不包含该事件名, 要给该事件一个初始化状态， 就是在 lastState 里面加上一个新的键，而已
          val newEvent: EventUpdate = initEvent(data, eventName)
          lastState.put(eventName, newEvent)
          result.append(newEvent)
        }
      }

      lastState

    } else { // 如果 state 不存在, 说明这一批都是新数据, 则使用 event 数据源创建一个新的 eventUpdate 并返回这个对象，不操作内存中的 state


      val dataEvents: mutable.HashMap[String, Integer] = data.eventMaps // 获取 event 数据源 Map 中的事件
      val eventUpdateMaps: mutable.HashMap[String, EventUpdate] = scala.collection.mutable.HashMap() // 存放输出事件的 Map

      dataEvents.map { ev =>
        val eventName = ev._1
        val newEvent: EventUpdate = initEvent(data, eventName)
        eventUpdateMaps.put(eventName, newEvent)
        result.append(newEvent)
      }

      eventUpdateMaps
    }
  }

  /**
    * 初始化一个新的事件
    * @param data 数据源
    * @param eventName 事件名
    * @param state HashMap[eventName, EventUpdate]
    */
  def initState(data: Event, eventName: String, state: State[mutable.HashMap[String, EventUpdate]]) = {

    // 只有数据中存在该事件时才初始化
    if (data.eventMaps.contains(eventName)) {

      if (state.exists()) {
        val newEvent = initEvent(data, eventName)
        val lastState: mutable.HashMap[String, EventUpdate] = state.get()
        lastState.put(eventName, newEvent)
        state.update(lastState)
      } else {
        val eventUpdateMaps: mutable.HashMap[String, EventUpdate] = scala.collection.mutable.HashMap() // 存放输出事件的 Map
        val newEvent = initEvent(data, eventName)
        eventUpdateMaps.put(eventName, newEvent)
        state.update(eventUpdateMaps)
      }
    }
  }

  // 初始化单个事件的输出对象
  def initEvent(data: Event, eventName: String): EventUpdate = {
    val vin            = data.vin
    val eventStartTime = data.ts
    val eventEndTime   = data.ts
    val eventStatus    = 1
    val eventType      = 1
    val startMileage   = data.veh_odo

    EventUpdate(
      vin,
      eventName,
      eventStartTime,
      eventEndTime,
      eventStatus,
      eventType,
      startMileage
    )
  }
}
