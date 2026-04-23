#!/usr/bin/env bash
# Choir Manager launcher — requires Java 21
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/choir-manager-1.1.0.jar"
java \
  --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
  --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
  -jar "$JAR" "$@"
