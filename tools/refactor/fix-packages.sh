#!/usr/bin/env bash
# Uso: tools/refactor/fix-packages.sh <directorio-o-fichero>...
# Reescribe la linea `package` de cada .kt para que coincida con su ruta
# (lo que va despues de /kotlin/, con / -> .). Idempotente.
set -euo pipefail
for target in "$@"; do
  find "$target" -type f -name '*.kt' -not -path '*/build/*' | while read -r file; do
    rel="${file#*/kotlin/}"
    pkg="$(dirname "$rel" | tr '/' '.')"
    PKG="$pkg" perl -pi -e 's/^package [\w.]+/package $ENV{PKG}/' "$file"
  done
done
