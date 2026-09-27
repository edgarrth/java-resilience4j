#!/usr/bin/env bash
set -euo pipefail

RESET_AFTER_MS="${1:-0}"

curl --fail --silent --show-error -X POST http://localhost:8474/reset >/dev/null
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"reset-peer-downstream\",\"type\":\"reset_peer\",\"stream\":\"downstream\",\"toxicity\":1.0,\"attributes\":{\"timeout\":${RESET_AFTER_MS}}}" \
  http://localhost:8474/proxies/psp/toxics >/dev/null

echo "Reset TCP aplicado después de ${RESET_AFTER_MS} ms."
