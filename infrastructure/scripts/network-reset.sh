#!/usr/bin/env bash
set -euo pipefail

# Deja el proxy habilitado y elimina todos los toxics activos.
curl --fail --silent --show-error -X POST http://localhost:8474/reset >/dev/null
echo "Toxiproxy quedó sin fallas de red inyectadas."
