#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${GRADLE_HOME:?Set GRADLE_HOME to a Gradle 8.13 distribution}"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
LIB="$GRADLE_HOME/lib"
CP="$LIB/kotlin-stdlib-2.0.21.jar:$LIB/kotlinx-coroutines-core-jvm-1.6.4.jar:$LIB/junit-4.13.2.jar:$LIB/hamcrest-core-1.3.jar:$OUT"
MAIN=app/src/main/java/com/nameemrooz/journal
TEST=app/src/test/java/com/nameemrooz/journal
java com.sun.tools.javac.Main -d "$OUT" "$MAIN/util/PersianEditorial.java" "$MAIN/speech/StableTranscriptMerger.java" "$MAIN/speech/PersonalVocabulary.java" "$MAIN/speech/RnntModelSpec.java"
mapfile -t SOURCES < <(find "$MAIN/writing" -name '*.kt' ! -name LocalPunctuationModel.kt ! -name MlKitNanoPromptClient.kt)
mapfile -t TESTS < <(find "$TEST/writing" -name '*Test.kt')
java -cp "$LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$OUT" "${SOURCES[@]}" "${TESTS[@]}"
mapfile -t CLASSES < <(find "$TEST/writing" -name '*Test.kt' -printf '%f\n' | sed 's/\.kt$//;s/^/com.nameemrooz.journal.writing./')
java -cp "$CP" org.junit.runner.JUnitCore "${CLASSES[@]}"
