#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PAYMENT_URL="${PAYMENT_URL:-http://localhost:8080}"

while IFS= read -r row; do
  [ -z "$row" ] && continue
  key=$(printf '%s' "$row" | python3 -c 'import json,sys; print(json.load(sys.stdin)["idempotencyKey"])')
  body=$(printf '%s' "$row" | python3 -c 'import json,sys; d=json.load(sys.stdin); d.pop("idempotencyKey"); print(json.dumps(d))')
  curl --fail-with-body --silent --show-error \
    -X POST \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${key}" \
    -d "$body" \
    "${PAYMENT_URL}/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE"
  echo
 done < "${ROOT_DIR}/datasets/payments.jsonl"
