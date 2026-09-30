#!/usr/bin/env bash
# Rebuild checked-in shaders with the SDK's exact matc version (Filament v1.57.1).
# Usage: MATC=/path/to/filament/bin/matc lottery/tools/compile-materials.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
compiler="${MATC:-matc}"
if [[ "$("$compiler" --version)" != "57" ]]; then
  echo 'Requires matc from Filament v1.57.1 (material version 57).' >&2
  exit 1
fi
for source in "$ROOT"/tools/materials/*.mat; do
  name="$(basename "$source" .mat)"
  "$compiler" -a opengl -p mobile -o "$ROOT/app/src/main/assets/$name.filamat" "$source"
done
