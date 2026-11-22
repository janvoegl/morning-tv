#!/usr/bin/env bash
# Tests scripts/install.sh against a fake adb and gradlew (no device needed).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fails=0
check() { if eval "$2"; then echo "ok   - $1"; else echo "FAIL - $1"; fails=$((fails+1)); fi; }

setup() { # $1 = config content, $2 = key the fake app ignores
  T="$(mktemp -d)"; mkdir -p "$T/scripts" "$T/config" "$T/bin"
  cp "$ROOT/scripts/install.sh" "$T/scripts/"
  echo "export PATH=\"$T/bin:\$PATH\"" > "$T/scripts/env.sh"
  printf '#!/bin/sh\nexit 0\n' > "$T/gradlew"
  cat > "$T/bin/adb" <<FAKE
#!/usr/bin/env bash
echo "\$*" >> "$T/adb.log"
if [[ "\$*" == *"am broadcast"* ]]; then
  key=\$(echo "\$*" | sed -E 's/.*--es key ([^ ]+) .*/\1/')
  if [ "\$key" = "$2" ]; then echo 'Broadcast completed: result=0, data="ignored"'; else echo "Broadcast completed: result=0, data=\"ok:\$key\""; fi
fi
FAKE
  chmod +x "$T/gradlew" "$T/bin/adb"
  printf '%s\n' "$1" > "$T/config/local.properties"
}

setup $'tv_host=192.0.2.10\ntv_psk=Sekr3tValue\ngreeting_name=Alex Smith' ""
out=$("$T/scripts/install.sh" 2>&1); code=$?
check "succeeds when every key is confirmed" "[ $code -eq 0 ]"
check "reports set keys" "grep -q 'set tv_psk' <<<\"\$out\""
check "never prints a value" "! grep -q 'Sekr3tValue' <<<\"\$out\""
check "includes stopped packages (-f 0x20)" "grep -q -- '-f 0x20' '$T/adb.log'"
rm -rf "$T"

setup $'tv_host = 192.0.2.10\ntv_psk = Sekr3tValue' ""
out=$("$T/scripts/install.sh" 2>&1); code=$?
check "trims spaces around '='" "[ $code -eq 0 ] && grep -q 'set tv_psk' <<<\"\$out\" && grep -q -- '--es key tv_psk ' '$T/adb.log'"
rm -rf "$T"

setup $'tv_host=192.0.2.10\ntv_psk=Sekr3tValue\ngreeting_name=Alex' "greeting_name"
out=$("$T/scripts/install.sh" 2>&1); code=$?
check "fails when the app ignores a key" "[ $code -ne 0 ] && grep -q 'greeting_name' <<<\"\$out\""
rm -rf "$T"

[ $fails -eq 0 ] && echo "all install tests passed" || { echo "$fails failed"; exit 1; }
