@echo off
REM Choir Manager launcher — requires Java 21
set DIR=%~dp0
java ^
  --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED ^
  --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED ^
  -jar "%DIR%choir-manager-1.1.0.jar" %*
