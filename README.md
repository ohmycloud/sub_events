## 模拟实时数据流

运行 perl6 fake-streaming.pl6, 每隔 5 秒往 0.0.0.0 地址的 3333 Socket 端口发送一行 JSON 样本数据：

```
{'vin':'LSJA0000000000091','ts':1547727950000,'veh_odo':0,'alm_common_temp_diff':0,'alm_common_temp_high':1,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547727955000,'veh_odo':1,'alm_common_temp_diff':1,'alm_common_temp_high':1,'alm_common_esd_high':0}
{'vin':'LSJA0000000000091','ts':1547727960000,'veh_odo':2,'alm_common_temp_diff':1,'alm_common_temp_high':1,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547727965000,'veh_odo':3,'alm_common_temp_diff':0,'alm_common_temp_high':1,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547727970000,'veh_odo':4,'alm_common_temp_diff':0,'alm_common_temp_high':1,'alm_common_esd_high':0}
{'vin':'LSJA0000000000091','ts':1547727975000,'veh_odo':5,'alm_common_temp_diff':0,'alm_common_temp_high':0,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547727980000,'veh_odo':6,'alm_common_temp_diff':1,'alm_common_temp_high':0,'alm_common_esd_high':0}
{'vin':'LSJA0000000000091','ts':1547727985000,'veh_odo':7,'alm_common_temp_diff':0,'alm_common_temp_high':1,'alm_common_esd_high':0}
{'vin':'LSJA0000000000091','ts':1547727990000,'veh_odo':8,'alm_common_temp_diff':0,'alm_common_temp_high':0,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547727995000,'veh_odo':9,'alm_common_temp_diff':0,'alm_common_temp_high':0,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547728000000,'veh_odo':10,'alm_common_temp_diff':1,'alm_common_temp_high':0,'alm_common_esd_high':0}
{'vin':'LSJA0000000000091','ts':1547728005000,'veh_odo':11,'alm_common_temp_diff':0,'alm_common_temp_high':1,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547728010000,'veh_odo':12,'alm_common_temp_diff':0,'alm_common_temp_high':1,'alm_common_esd_high':1}
{'vin':'LSJA0000000000091','ts':1547728015000,'veh_odo':13,'alm_common_temp_diff':1,'alm_common_temp_high':1,'alm_common_esd_high':0}
```

该 Streaming 程序的任务是解析 JSON 流， JSON 中每个字段的意义如下：

```scala
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
```

程序的输出是一个 **EventUpdate** 类型的数组, EventUpdate 定义了每个事件的信息：

```scala
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
```

其中 **alm_common_temp_diff**, **alm_common_temp_high**, **alm_common_esd_high** 都是事件, 当其值为 1 时, 表示发生了该事件; 当其值为 0 时, 表示没有发生该事件。

要求输出这三种类型的事件：

a)、当事件第一次发生时①  
b)、俩个事件之前的事件之差超过 15 秒时, 则结束上一个事件②, 并开始一个新的事件③;  

## 配置文件

resources 目录下的文件：

- application.conf

```
spark {
  master = "local[4]"
  streaming.batch.duration = 5000
  eventLog.enabled         = true
  ui.enabled               = true
  ui.port                  = 4040
  metrics.conf             = metrics.properties
  spark.cleaner.ttl        = 3600
  checkpoint.path          = "/tmp/telematics"
  spark.cleaner.referenceTracking.cleanCheckpoints = true
}

socket {
  host = "localhost"
  port = 3333
}
```

- log4j.properties

```
# Set everything to be logged to the console
log4j.rootCategory=ERROR, console
log4j.appender.console=org.apache.log4j.ConsoleAppender
log4j.appender.console.target=System.out
log4j.appender.console.layout=org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%d{yy/MM/dd HH:mm:ss} %l %p %c{1}: %m%n

# Settings to quiet third party logs that are too verbose
log4j.logger.org.spark-project.jetty=ERROR
log4j.logger.org.spark-project.jetty.util.component.AbstractLifeCycle=ERROR
log4j.logger.org.apache.spark.repl.SparkIMain$exprTyper=INFO
log4j.logger.org.apache.spark.repl.SparkILoop$SparkILoopInterpreter=INFO
log4j.logger.org.apache.parquet=ERROR
log4j.logger.parquet=ERROR

# SPARK-9183: Settings to avoid annoying messages when looking up nonexistent UDFs in SparkSQL with Hive support
log4j.logger.org.apache.hadoop.hive.metastore.RetryingHMSHandler=FATAL
log4j.logger.org.apache.hadoop.hive.ql.exec.FunctionRegistry=ERROR
```

- metrics.properties

```
*.sink.jmx.class=org.apache.spark.metrics.sink.JmxSink
*.sink.servlet.class=org.apache.spark.metrics.sink.MetricsServlet
*.sink.slf4j.class=org.apache.spark.metrics.sink.Slf4jSink
*.sink.slf4j.period=5
*.sink.slf4j.unit=seconds
```

## 运行方式

先启动上面的实时数据流模拟程序： perl6 fake-streaming.pl6，再开启另外一个窗口执行：./spark-submit.sh. 观察程序的输出。

## 输出结果

程序的输出显示如下：

```
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_high,1547727950000,1547727950000,1,1,0.0), EventUpdate(LSJA0000000000091,alm_common_esd_high,1547727950000,1547727950000,1,1,0.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547727955000,1547727955000,1,1,1.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547727955000,1547727960000,0,1,1.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547727980000,1547727980000,1,1,6.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547727980000,1547727980000,0,1,6.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728000000,1547728000000,1,1,10.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_high,1547727950000,1547727985000,0,1,0.0), EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728005000,1547728005000,1,1,11.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728000000,1547728020000,0,1,10.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728040000,1547728040000,1,1,18.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728040000,1547728040000,0,1,18.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728060000,1547728060000,1,1,22.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728005000,1547728045000,0,1,11.0), EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728065000,1547728065000,1,1,23.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728060000,1547728080000,0,1,22.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728100000,1547728100000,1,1,30.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728100000,1547728100000,0,1,30.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728120000,1547728120000,1,1,34.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728065000,1547728105000,0,1,23.0), EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728125000,1547728125000,1,1,35.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728120000,1547728140000,0,1,34.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728160000,1547728160000,1,1,42.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728160000,1547728160000,0,1,42.0), EventUpdate(LSJA0000000000091,alm_common_temp_diff,1547728180000,1547728180000,1,1,46.0)))
(LSJA0000000000091,ArrayBuffer(EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728125000,1547728165000,0,1,35.0), EventUpdate(LSJA0000000000091,alm_common_temp_high,1547728185000,1547728185000,1,1,47.0)))
```