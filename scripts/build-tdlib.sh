#!/usr/bin/env bash
# Compila o TDLib oficial para Android (interface Java/JNI) e coloca o resultado em ./tdlib:
#   tdlib/java/org/drinkless/tdlib/{Client,TdApi}.java  e  tdlib/libs/<abi>/libtdjni.so
# Requer Linux com: git cmake gperf make perl php sed tar unzip zip curl e JDK.
# Uso: scripts/build-tdlib.sh [ref-do-td]   (demora ~1 h; o CI guarda em cache)
set -euo pipefail
TD_REF="${1:-${TD_REF:-master}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TD_WORK_DIR:-$ROOT/build/td-src}"

rm -rf "$WORK" && mkdir -p "$WORK"
git clone --filter=blob:none https://github.com/tdlib/td.git "$WORK/td"
git -C "$WORK/td" checkout "$TD_REF"
echo "TDLib commit: $(git -C "$WORK/td" rev-parse HEAD)"

cd "$WORK/td/example/android"
ls
# Passos do README oficial (td/example/android), com os valores padrão de SDK/NDK/OpenSSL.
./check-environment.sh
./fetch-sdk.sh
./build-openssl.sh
./build-tdlib.sh
find tdlib -maxdepth 3

rm -rf "$ROOT/tdlib" && mkdir -p "$ROOT/tdlib"
cp -r tdlib/java "$ROOT/tdlib/java"
cp -r tdlib/libs "$ROOT/tdlib/libs"
git -C "$WORK/td" rev-parse HEAD > "$ROOT/tdlib/COMMIT"
find "$ROOT/tdlib" -maxdepth 4 | sort
