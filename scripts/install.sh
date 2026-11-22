#!/usr/bin/env bash
# Builds the app, installs it on the TV over ADB and pushes config/local.properties
# into the app. Env MTV_TV_PSK overrides tv_psk.
# Turn on "Network debugging" on the TV first; turn it off again afterwards.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
source scripts/env.sh
CFG="config/local.properties"
[ -f "$CFG" ] || { echo "missing $CFG (copy config/example.properties)" >&2; exit 1; }

declare -A conf
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in ''|\#*) continue;; esac
  key="${line%%=*}"; value="${line#*=}"
  key="$(echo "$key" | xargs)"; value="${value#"${value%%[![:space:]]*}"}"; value="${value%"${value##*[![:space:]]}"}"
  conf["$key"]="$value"
done < "$CFG"
[ -n "${MTV_TV_PSK:-}" ] && conf[tv_psk]="$MTV_TV_PSK"
HOST="${conf[tv_host]:-}"
[ -n "$HOST" ] || { echo "tv_host is not set in $CFG" >&2; exit 1; }
DEV="$HOST:5555"

./gradlew :app:assembleDebug --console=plain -q
adb connect "$DEV" >/dev/null
adb -s "$DEV" install -r app/build/outputs/apk/debug/app-debug.apk
quote() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }
for key in "${!conf[@]}"; do
  case "$key" in tv_host|home_wifi) continue;; esac
  # -f 0x20 = FLAG_INCLUDE_STOPPED_PACKAGES: a freshly installed app is "stopped"
  reply=$(adb -s "$DEV" shell am broadcast -f 0x20 -n io.github.morningtv/.config.ConfigReceiver \
    -a io.github.morningtv.SET_CONFIG --es key "$key" --es value "$(quote "${conf[$key]}")")
  if [[ "$reply" != *"ok:$key"* ]]; then
    echo "the app did not accept '$key' (unknown key or app not reachable)" >&2
    adb disconnect "$DEV" >/dev/null; exit 1
  fi
  echo "set $key"
done
adb disconnect "$DEV" >/dev/null
echo "installed on $HOST"
