#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"enabled":true}' \
  http://localhost:8474/proxies/psp >/dev/null

echo "Proxy PSP habilitado nuevamente."
