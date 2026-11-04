@echo off
rem Duplo clique no Windows: compila e dispara o report.
rem   report.bat               dispara com a data de hoje
rem   report.bat --previa      so imprime o texto, sem enviar
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests package || exit /b 1
java -jar target\report-bot-1.0.0.jar %*
pause
