#!/bin/sh
./gradlew -Pskip-signing=true check \
  -x :phonenumber:watchosSimulatorArm64Test \
  -x :phonenumber:tvosSimulatorArm64Test \
  -x :phonenumber:compileTestDevelopmentExecutableKotlinWasmJs \
  publishToMavenLocal
exit 0
