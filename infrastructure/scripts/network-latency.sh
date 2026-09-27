#!/usr/bin/env bash
set -euo pipefail

LATENCY_MS="${1:-1200}"
JITTER_MS="${2:-100}"

# Primero limpiamos el estado para que el escenario sea repetible.
curl --fail --silent --show-error -X POST http://localhost:8474/reset >/dev/null
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"latency-downstream\",\"type\":\"latency\",\"stream\":\"downstream\",\"toxicity\":1.0,\"attributes\":{\"latency\":${LATENCY_MS},\"jitter\":${JITTER_MS}}}" \
  http://localhost:8474/proxies/psp/toxics >/dev/null

echo "Latencia de red aplicada: ${LATENCY_MS} ms +/- ${JITTER_MS} ms."
