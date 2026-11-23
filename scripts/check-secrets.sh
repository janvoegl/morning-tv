#!/usr/bin/env bash
# Fails if config/local.properties is tracked/staged, or if any of its values
# (4+ characters) appear in staged changes or tracked files. Prints only file
# and key names, never the values.
set -uo pipefail
ROOT="$(git rev-parse --show-toplevel)"; cd "$ROOT"
LOCAL="config/local.properties"
# Keys whose values are not personal (they also appear in the example file)
PUBLIC_KEYS=" timezone standby_minutes calendar_style layout "
fail=0
if git ls-files --error-unmatch "$LOCAL" >/dev/null 2>&1 || git diff --cached --name-only | grep -qx "$LOCAL"; then
  echo "check-secrets: $LOCAL is tracked or staged — remove it from git" >&2; fail=1
fi
if [ -f "$LOCAL" ]; then
  while IFS='=' read -r key value; do
    case "$key" in ''|\#*) continue;; esac
    case "$PUBLIC_KEYS" in *" $key "*) continue;; esac
    [ "${#value}" -ge 4 ] || continue
    hits=$( { git grep -l -F -e "$value" -- . ":!$LOCAL" 2>/dev/null; \
              git diff --cached -U0 -- . ":!$LOCAL" | grep -q -F -e "$value" && echo "(staged changes)"; } | sort -u)
    if [ -n "$hits" ]; then
      echo "check-secrets: value of '$key' found in: $(echo $hits)" >&2; fail=1
    fi
  done < "$LOCAL"
fi
exit $fail
