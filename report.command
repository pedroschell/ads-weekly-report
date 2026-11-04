#!/bin/zsh
# Duplo clique no Mac: compila (se preciso) e dispara o report.
#   ./report.command                  dispara com a data de hoje
#   ./report.command 2026-11-12       simula outra data
#   ./report.command --previa         so imprime o texto, sem enviar
cd "$(dirname "$0")"
[ -d /opt/homebrew/opt/openjdk@21 ] && export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && export PATH="$JAVA_HOME/bin:$PATH"
JAR=target/report-bot-1.0.0.jar
if [ ! -f "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" 2>/dev/null | head -1)" ]; then
  ./mvnw -q -DskipTests package || exit 1
fi
java -jar "$JAR" "$@"
