#!/usr/bin/env bash
# Regenera PARCIAL2.pdf a partir de PARCIAL2.md.
# Requiere: google-chrome (o chromium) y las librerías de render en este directorio.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
DOC="$HERE/.."
python3 "$HERE/build.py" "$DOC/PARCIAL2.md"
google-chrome --headless=new --disable-gpu --no-sandbox \
  --virtual-time-budget=90000 --run-all-compositor-stages-before-draw \
  --no-pdf-header-footer --print-to-pdf="$DOC/PARCIAL2.pdf" "file://$HERE/index.html"
echo "PARCIAL2.pdf regenerado"
