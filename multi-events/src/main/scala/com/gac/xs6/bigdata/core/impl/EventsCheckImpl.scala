package com.gac.xs6.bigdata.core.impl

import com.datastax.spark.connector.util.Logging
import com.gac.xs6.bigdata.core.EventsCheck
import com.gac.xs6.bigdata.model.{Event, EventUpdate}
import org.apache.spark.streaming.{Duration, State, StateSpec}
import org.apache.spark.streaming.dstream.DStream
import scala.collection.mutable

/**
  * 事件检测
  */
object EventsCheckImpl extends EventsCheck with Logging {

  val eventIdleTimeout: Duration = Duration(15 * 60 * 1000) // 超时时间设置为 15 分钟
  val eventDuration = Duration( 15 * 1000) // 俩个 event 之间的时间之差为 15秒（结束这个事件）, 生产环境为 30s

  /**
    * 将每辆车的事件流转为事件状态流
    * @param stream (vin, Event) 事件流
    * @return 事件状态流,(vin, HashMap[eventName, eventState])
    */
  override def extract(stream: DStream[(String, Event)]): DStream[(String,  mutable.HashMap[String,EventUpdate])] = {
    stream.mapWithState(eventStateSpec)
      .flatMap(opt => opt)
  }

  val eventStateSpec = StateSpec.function(mappingEvent _).timeout(eventIdleTimeout)

  /**
    *
    * @param vin 车架号,
    * @param opt Event 事件流
    * @param state HashMap[eventName, EventUpdate]
    * @return (vin, HashMap[eventName, EventUpdate])
    */
  def mappingEvent(vin: String, opt: Option[Event], state: State[mutable.HashMap[String, EventUpdate]]): Option[(String, mutable.HashMap[String, EventUpdate])] = {
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

    val sinkUpdate: mutable.HashMap[String, EventUpdate] = scala.collection.mutable.HashMap() // 全局的存放输出结果

    if (state.isTimingOut() && state.exists()) {
      val lastState = state.get()

      lastState.map { ev => ev._2.eventStatus = 2 }  // 超时的事件, 状态置为 2
      Some((vin, lastState))                         // 发送到 downstream, 但不移除状态
    } else opt.flatMap( data => {

      val dataEvents = data.eventMaps            // data 中的 eventMaps
      val newTime    = data.ts                   // 新的数据的时间戳
      val lastState  = getLastState(data, state) // 获取每个事件上一次的状态, state 中的 eventMaps, 只获取
      val cc = lastState
      val dd = state

      eventsList.map { evName =>

        if (dataEvents.contains(evName) &&
             lastState.contains(evName)
        ) {
          // 判断事件结束，初始化或更新
          lastState.map { ev =>
            // 对于每一个事件, 用 newTime - eventEndTime
            val evName   = ev._1
            val evUpdate = ev._2
            if ( (newTime - evUpdate.eventEndTime) >= eventDuration.milliseconds ) { // 事件正常结束
              println("开始划分事件了： 事件名, 事件开始时间, 事件结束时间, 事件持续时长", evName, evUpdate.eventStartTime, evUpdate.eventEndTime, evUpdate.eventDuration)
              evUpdate.eventStatus  = 0        // 这种类型的事件是正常结束的事件, 将事件类型置为 0, 更新了 lastState
              evUpdate.eventEndTime = newTime  // 把新的源数据的时间戳作为上一个事件的事件结束时间, 更新了 lastState

              initState(data, evName, state)  // 初始化新的事件

            } else { // 事件正在进行
              val newState = getUpdateState(data, evName, lastState)
              state.update(newState) // 用 newState 更新旧的状态
              None  // 在内存中更新事件, 不返回东西
            }
          }

        } else if (dataEvents.contains(evName) &&
                    !lastState.contains(evName)
        ) {

          initState(data, evName, state) // 新建 state, 需要更新 state

        } else if (!dataEvents.contains(evName) &&
                     lastState.contains(evName)
        ) {

          // 更新事件
          lastState.map { ev =>
            val updateState = getUpdateState(data, ev._1, lastState)
            state.update(updateState)
            None  // 在内存中更新事件, 不返回东西
          }

          //None // 原始数据中没有, 但是 state 中有， 那什么也不做
        } else {
          None
        }
      }

      if (lastState.size > 0) {
        Some(vin, lastState)
      } else {
        None
      }
    })
  }

  /**
    * 事件正在进行, 则更新内存中的 state 对象, 不划分
    * @param event 数据源
    * @param evName 事件名
    * @param lastState 上一个 event 的状态
    * @return
    */
  def getUpdateState(data: Event, evName: String, lastState: mutable.HashMap[String,EventUpdate]): mutable.HashMap[String,EventUpdate] = {

    val ts: Long = data.ts

    val eventMaps = data.eventMaps
    if (eventMaps.contains(evName)) {     // 如果有需要更新的事件, 则更新
      lastState(evName).eventEndTime = ts // 事件正在进行, 将上一个事件的事件结束时间置为新的数据源的 ts
      lastState(evName).eventStatus  = 1  // 事件正在进行, 这这种类型的事件状态置为 1
    }

    lastState
  }

  /**
    * 获取每个事件的上一次的状态
    * @param data 事件的数据源
    * @param state HashMap[evName, EventUpdate]
    * @return HashMap[evName, EventUpdate]
    */
  def getLastState(data: Event, state: State[mutable.HashMap[String, EventUpdate]]): mutable.HashMap[String,EventUpdate] = {

    if (state.exists()) {
      val dataEvents = data.eventMaps // 获取 data 中的 events
      val lastState = state.get()

      dataEvents.map { ev =>
        val evName = ev._1
        if (lastState.contains(evName)) { // state 里面包含已经出现的事件, 什么也不做，最后原样取回
            None  // 什么也不做, 没有副作用
        } else {  // state 中不包含该事件名, 要给该事件一个初始化状态， 就是在 lastState 里面加上一个新的键，而已
          val newEvent = initEventUpdate(evName, data)
          lastState.put(evName, newEvent)
        }
      }

      lastState

    } else { // 如果 state 不存在, 说明这一批都是新数据, 则使用 event 数据源创建一个新的 eventUpdate 并返回这个对象，不操作内存中的 state
      val dataEvents: mutable.HashMap[String, Integer] = data.eventMaps // 获取 event 数据源 Map 中的事件
      val eventUpdateMaps: mutable.HashMap[String, EventUpdate] = scala.collection.mutable.HashMap() // 存放输出事件的 Map

      dataEvents.map { ev =>
        val evName = ev._1
        val newEventUpdate = initEventUpdate(evName, data)
        eventUpdateMaps.put(evName, newEventUpdate)
      }

      eventUpdateMaps
    }
  }

  /**
    * 初始化一个新的事件
    * @param event 数据源
    * @param evName 事件名
    * @param state HashMap[eventName, EventUpdate]
    */
  def initState(data: Event, evName: String, state: State[mutable.HashMap[String, EventUpdate]]) = {

    val newEvent = initEventUpdate(evName, data)
    val lastState: mutable.HashMap[String, EventUpdate] = state.get()
    lastState.put(evName, newEvent)
    state.update(lastState)
  }

  // 初始化单个事件的输出对象
  def initEventUpdate(evName: String, ev: Event): EventUpdate = {
    val vin            = ev.vin
    val eventStartTime = ev.ts
    val eventEndTime   = ev.ts
    val eventStatus    = 1
    val eventType      = 1
    val startMileage   = ev.veh_odo

    EventUpdate(
      vin,
      evName,
      eventStartTime,
      eventEndTime,
      eventStatus,
      eventType,
      startMileage
    )
  }
}
