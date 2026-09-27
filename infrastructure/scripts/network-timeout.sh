#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_MS="${1:-4000}"

curl --fail --silent --show-error -X POST http://localhost:8474/reset >/dev/null
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"timeout-downstream\",\"type\":\"timeout\",\"stream\":\"downstream\",\"toxicity\":1.0,\"attributes\":{\"timeout\":${TIMEOUT_MS}}}" \
  http://localhost:8474/proxies/psp/toxics >/dev/null

echo "Timeout de red aplicado: ${TIMEOUT_MS} ms."
