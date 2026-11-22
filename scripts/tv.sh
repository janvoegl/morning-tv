#!/usr/bin/env bash
# Small helper for the Sony BRAVIA REST API.
#   scripts/tv.sh status | on | off | apps | launch <uri> [data]
# Reads tv_host / tv_psk from config/local.properties; env MTV_TV_PSK overrides tv_psk.
# The key is sent as a header and never printed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CFG="$ROOT/config/local.properties"
[ -f "$CFG" ] || { echo "missing $CFG (copy config/example.properties)" >&2; exit 1; }
get() { grep -E "^$1=" "$CFG" | head -1 | cut -d= -f2-; }
HOST="$(get tv_host)"; PSK="${MTV_TV_PSK:-$(get tv_psk)}"
[ -n "$HOST" ] || { echo "tv_host not set" >&2; exit 1; }
[ -n "$PSK" ] || { echo "tv_psk not set (or MTV_TV_PSK)" >&2; exit 1; }

call() { # service method params-json
  curl -s -m 8 -H "X-Auth-PSK: $PSK" -H "Content-Type: application/json" -X POST \
    -d "{\"method\":\"$2\",\"id\":1,\"params\":$3,\"version\":\"1.0\"}" "http://$HOST/sony/$1"
  echo
}
json_str() { python3 -c 'import json,sys;print(json.dumps(sys.argv[1]))' "$1"; }

case "${1:-}" in
  status) call system getPowerStatus '[]' ;;
  on)     call system setPowerStatus '[{"status":true}]' ;;
  off)    call system setPowerStatus '[{"status":false}]' ;;
  apps)   call appControl getApplicationList '[]' ;;
  launch)
    [ -n "${2:-}" ] || { echo "usage: tv.sh launch <uri> [data]" >&2; exit 1; }
    if [ -n "${3:-}" ]; then
      call appControl setActiveApp "[{\"uri\":$(json_str "$2"),\"data\":$(json_str "$3")}]"
    else
      call appControl setActiveApp "[{\"uri\":$(json_str "$2")}]"
    fi ;;
  *) echo "usage: tv.sh status|on|off|apps|launch <uri> [data]" >&2; exit 1 ;;
esac
