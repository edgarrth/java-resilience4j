#!/usr/bin/env bash
set -euo pipefail

# Esta validación usa exactamente Maven 3.9.16 y JDK 25 sin depender del Java instalado en la máquina.
docker run --rm \
  -v "$(pwd):/workspace" \
  -v "${HOME}/.m2:/root/.m2" \
  -w /workspace \
  maven:3.9.16-eclipse-temurin-25 \
  mvn -B clean verify
