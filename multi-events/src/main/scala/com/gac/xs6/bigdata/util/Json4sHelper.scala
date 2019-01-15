package com.gac.xs6.bigdata.util

import java.util.Locale

import org.joda.time.Instant
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.JString
import org.json4s.{CustomSerializer, DefaultFormats}

import scala.util.Try

/**
  * Created by azhe on 18-4-3.
  */
object Json4sHelper {
  object StringToBigDecimal extends CustomSerializer[BigDecimal](format => (
    {
      case JString(x) =>  Try{
      BigDecimal(x.toDouble)
    }.recover {
      case e: Exception => BigDecimal(0) //适用于OBDData中所有的Decimal类型数据
    }.get },
    { case x: BigDecimal => JString(x.toString) }
    ))

  object StringToDouble extends CustomSerializer[Double](format => (
    {
      case JString(x) => Try{
      x.toDouble
    }.recover {
      case e: Exception => "0.0".toDouble
    }.get },
    { case x: Double => JString(x.toString) }
    ))

  object StringToInt extends CustomSerializer[Int](format => (
    {
      case JString(x) => Try{
      x.toInt
    }.recover {
      case e: Exception => "0".toInt
    }.get },
    { case x: Int => JString(x.toString) }
    ))

  object StringToLong extends CustomSerializer[Long](format => (
    {
      case JString(x) => Try{
        x.toLong
      }.recover {
        case e: Exception => "0".toLong
      }.get },
    { case x: Long => JString(x.toString) }
    ))

  val dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CHINA)
  val dateTimeFormatter2 = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss SSS").withLocale(Locale.CHINA)

  object StringToInstant extends CustomSerializer[Instant](format => ( {
    case JString(t) => Try{
      Instant.parse(t)
    }.recover {
      case e: IllegalArgumentException => Instant.parse(t, dateTimeFormatter)
    }.recover {
      case e: IllegalArgumentException => Instant.parse(t, dateTimeFormatter2)
    }.recover {
      case e: IllegalArgumentException => Instant.parse("1970-01-01 00:00:00",DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC())
    }.get
  }, {
    case x: Instant => JString(x.toString)
  }))

  val CihonFormats = DefaultFormats + StringToBigDecimal + StringToInt + StringToDouble + StringToInstant+StringToLong
}
