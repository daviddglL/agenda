#!/usr/bin/env bash
# Uso: tools/refactor/rename-fqn.sh <fichero-de-mapeo>
# Cada linea: "<fqn-o-paquete-viejo> <fqn-o-paquete-nuevo>". Se ignoran lineas vacias y las
# que empiezan por #. Se aplican de la mas larga a la mas corta.
set -euo pipefail
map="$1"
grep -v '^[[:space:]]*#' "$map" | awk 'NF == 2 { print length($1) "\t" $1 "\t" $2 }' \
  | sort -rn | cut -f2- | while IFS=$'\t' read -r old new; do
    find androidApp shared core feature -type f \( -name '*.kt' -o -name '*.kts' \) \
      -not -path '*/build/*' -print0 \
      | OLD="$old" NEW="$new" xargs -0 perl -pi -e 's/\Q$ENV{OLD}\E(?![A-Za-z0-9_])/$ENV{NEW}/g'
  done
