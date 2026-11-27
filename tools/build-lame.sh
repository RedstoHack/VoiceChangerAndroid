#!/usr/bin/env bash
# Собирает libvcmp3.so (кодер MP3: LAME 3.100 + тонкая JNI-обёртка из native/) для Android
# и кладёт в app/src/main/jniLibs/<abi>/. Нужен интернет и Android NDK (на раннерах GitHub они есть).
# LAME распространяется под LGPL; библиотека подключается динамически.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/build-lame"
LOG="$WORK/build.log"
mkdir -p "$WORK"
: > "$LOG"
trap 'echo "---- конец журнала сборки LAME ----"; tail -n 60 "$LOG" || true' ERR
cd "$WORK"

log() { echo "$*" | tee -a "$LOG"; }

# ---- исходники LAME (несколько зеркал)
if ! ls -d lame-3.100 >/dev/null 2>&1; then
  ok=0
  for url in \
    "https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz" \
    "https://deb.debian.org/debian/pool/main/l/lame/lame_3.100.orig.tar.gz" \
    "https://archive.ubuntu.com/ubuntu/pool/main/l/lame/lame_3.100.orig.tar.gz" \
    "https://sourceforge.net/projects/lame/files/lame/3.100/lame-3.100.tar.gz/download"; do
    log "скачиваю: $url"
    if curl -fsSL -A "Mozilla/5.0" --retry 3 --retry-delay 2 --max-time 120 -o lame.tgz "$url" >>"$LOG" 2>&1 \
       && tar tzf lame.tgz >/dev/null 2>&1; then ok=1; break; fi
    log "  не получилось"
  done
  [ "$ok" = 1 ] || { log "Не удалось скачать исходники LAME ни с одного зеркала"; exit 1; }
  tar xzf lame.tgz
fi
LAME="$(ls -d lame-3.100* | grep -v '\.tgz$' | head -n 1)"
[ -f "$LAME/libmp3lame/lame.c" ] || { log "В архиве нет libmp3lame/lame.c (папка: $LAME)"; exit 1; }
log "исходники LAME: $LAME"

NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
[ -n "$NDK" ] && [ -d "$NDK" ] || { log "Android NDK не найден"; exit 1; }
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
log "NDK: $NDK"
API=26

# все исходники кодера, кроме декодера mpglib; векторные (x86 SSE) не нужны
SRC=$(ls "$LAME"/libmp3lame/*.c | grep -v mpglib_interface)

built=0
for pair in arm64-v8a:aarch64-linux-android armeabi-v7a:armv7a-linux-androideabi x86_64:x86_64-linux-android; do
  abi="${pair%%:*}"; triple="${pair##*:}"
  CC="$TC/${triple}${API}-clang"
  [ -x "$CC" ] || { log "нет компилятора $CC"; exit 1; }
  OUT="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$OUT"
  log "== $abi"
  "$CC" -shared -fPIC -O2 -fno-strict-aliasing -std=gnu99 -w \
    -DSTDC_HEADERS=1 -DHAVE_STDINT_H=1 -DHAVE_LIMITS_H=1 -DHAVE_STRING_H=1 -DLAME_LIBRARY_BUILD=1 -DNDEBUG \
    -Wl,-z,max-page-size=16384 \
    -I "$LAME/include" -I "$LAME/libmp3lame" -I "$LAME" -I "$ROOT/native" \
    -o "$OUT/libvcmp3.so" \
    "$ROOT/native/vc_mp3.c" "$ROOT/native/vc_mp3_jni.c" $SRC -lm >>"$LOG" 2>&1
  "$TC/llvm-strip" --strip-unneeded "$OUT/libvcmp3.so" >>"$LOG" 2>&1 || true
  built=$((built+1))
done
log "Собрано библиотек: $built"
ls -la "$ROOT"/app/src/main/jniLibs/*/libvcmp3.so | tee -a "$LOG"
