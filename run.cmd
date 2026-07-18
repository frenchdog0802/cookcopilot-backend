@echo off
REM Always start the API with UTF-8 so Chinese chat streaming is not garbled on MS950 Windows.
cd /d "%~dp0"
.\mvnw.cmd spring-boot:run %*
