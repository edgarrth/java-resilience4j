#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"enabled":false}' \
  http://localhost:8474/proxies/psp >/dev/null

echo "Proxy PSP deshabilitado: el servicio queda inaccesible a nivel TCP."
