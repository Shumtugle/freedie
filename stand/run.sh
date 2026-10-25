#!/bin/sh
# Runs the game on the desktop stand: compiles every source file that does not touch the
# platform, together with the stand, plays a scene and writes, for each shot, the frame's
# draw list and flat layer. stand/web/index.html draws them with the game's shaders.
#
#   stand/run.sh [scene file] [output dir]      defaults: stand/scene.txt, out/stand
set -e
cd "$(dirname "$0")/.."
SCENE=${1:-stand/scene.txt}
OUT=${2:-out/stand}
rm -rf out/stand-classes
mkdir -p out/stand-classes "$OUT"
PURE=$(grep -rL -E '^import android\.|[^a-z.]android\.[a-z]+\.[A-Z]' --include='*.java' src || true)
if ! javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d out/stand-classes \
        $PURE $(find stand/src -name '*.java') 2>out/stand-javac.log; then
    grep -v JAVA_TOOL_OPTIONS out/stand-javac.log; exit 1
fi
java -Djava.awt.headless=true -cp out/stand-classes com.shumtugle.freedie.Stand "$SCENE" "$OUT" 2>&1 | grep -v JAVA_TOOL_OPTIONS
