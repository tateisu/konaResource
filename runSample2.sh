#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "$0")" && pwd)"
cd "$script_directory"

./gradlew publishLocalMaven
./gradlew sample2:runDebug
