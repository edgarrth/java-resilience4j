#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PSP_URL="${PSP_URL:-http://localhost:8081}"

# Carga los escenarios sin reiniciar el simulador y deja healthy como escenario inicial.
curl --fail --silent --show-error \
  -X POST \
  -H 'Content-Type: application/json' \
  --data-binary "@${SCRIPT_DIR}/scenarios.json" \
  "${PSP_URL}/admin/scenarios/import" >/dev/null

curl --fail --silent --show-error \
  -X POST \
  "${PSP_URL}/admin/scenarios/healthy/activate" >/dev/null

curl --fail --silent --show-error \
  -X POST \
  "${PSP_URL}/admin/reset" >/dev/null

echo "Escenarios cargados. El PSP quedó en modo healthy y con contadores limpios."
