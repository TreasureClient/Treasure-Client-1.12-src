#!/bin/bash
# Builds the jar for every supported Minecraft version into builds/ (or only the versions given).
# Needs Java 25 (set JAVA_HOME if it isn't the default).
cd "$(dirname "$0")/.."
for v in ${@:-1.21.11 26.1 26.1.2 26.2 26.3}; do
  rm -f build/libs/*.jar
  if ./gradlew build -Pmc=$v --no-daemon > build_$v.log 2>&1; then
    j=$(ls build/libs/*.jar 2>/dev/null | grep -v sources | head -1)
    cp "$j" builds/treasure-client-1.5.0+$v.jar && echo "$v OK" && rm -f build_$v.log
  else
    echo "$v FAILED (see build_$v.log)"
  fi
done
