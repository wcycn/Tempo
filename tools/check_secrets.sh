#!/usr/bin/env bash
set -euo pipefail

# This is a lightweight guard, not a replacement for GitHub secret scanning.
files=$(git ls-files | grep -Ev '(^|/)(\.env\.example|README.*|.*\.md)$' || true)
if [[ -z "$files" ]]; then
  exit 0
fi

patterns='(BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}|cc[0-9a-f]{20,}\.)'
if printf '%s\n' "$files" | xargs -r rg -n -I -e "$patterns" -- 2>/dev/null; then
  echo "Potential secret detected in tracked files. Remove it and rotate the credential." >&2
  exit 1
fi

echo "Secret guard passed"
