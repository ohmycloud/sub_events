#!/bin/sh

CONF_DIR=/Users/ohmycloud/demo/resources
APP_CONF=application.conf
EXECUTOR_JMX_PORT=23333
DRIVER_JMX_PORT=2334

spark-submit \
  --class com.gac.xs6.bigdata.EventsApplication \
  --master local[2] \
  --deploy-mode client \
  --driver-memory 2g \
  --driver-cores 2 \
  --executor-memory 1g \
  --executor-cores 2 \
  --num-executors 2 \
  --conf spark.executor.memoryOverhead=1024 \
  --conf spark.driver.memoryOverhead=1024 \
  --conf spark.yarn.maxAppAttempts=2 \
  --conf spark.yarn.submit.waitAppCompletion=false \
  --driver-class-path $CONF_DIR \
  --files $CONF_DIR/$APP_CONF,$CONF_DIR/log4j.properties,$CONF_DIR/metrics.properties \
  multi-events/target/multi-events-1.0-SNAPSHOT-shaded.jar