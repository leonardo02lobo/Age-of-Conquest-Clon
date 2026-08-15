#!/usr/bin/env python3
"""Genera index.html a partir de PARCIAL3.md, listo para imprimir a PDF con Chrome."""
import json, re, sys, pathlib

BUILD = pathlib.Path(__file__).parent
SRC = pathlib.Path(sys.argv[1])
md = SRC.read_text(encoding="utf-8")

# ---- frontmatter -----------------------------------------------------------
meta = {}
m = re.match(r"^---\n(.*?)\n---\n", md, re.S)
if m:
    body = m.group(1)
    md = md[m.end():]
    key = None
    for line in body.splitlines():
        if re.match(r"^\s*-\s", line) and key:
            meta.setdefault(key, []).append(line.strip()[2:].strip().strip('"'))
        elif ":" in line:
            key, _, val = line.partition(":")
            key = key.strip()
            val = val.strip().strip('"')
            if val:
                meta[key] = val

authors = meta.get("authors", [])
if isinstance(authors, str):
    authors = [authors]

cover = f"""
<section class="cover">
  <div class="uni">
    <p>Universidad Nacional Experimental del T&aacute;chira</p>
    <p>Vicerrectorado Acad&eacute;mico</p>
    <p>Decanato de Docencia</p>
    <p>{meta.get('course','')}</p>
  </div>
  <div class="title-block">
    <p class="parcial">{meta.get('parcial','')}</p>
    <h1 class="doctitle">{meta.get('title','')}</h1>
    <p class="subtitle">{meta.get('subtitle','')}</p>
  </div>
  <div class="authors">
    <p><strong>Autores:</strong></p>
    {''.join(f'<p>{a}</p>' for a in authors)}
  </div>
  <p class="place">{meta.get('place','')}, {meta.get('date','')}</p>
</section>
"""

html = f"""<!doctype html>
<html lang="es"><head>
<meta charset="utf-8">
<title>{meta.get('title','Documento')}</title>
<link rel="stylesheet" href="katex.css">
<link rel="stylesheet" href="style.css">
</head><body>
{cover}
<section id="toc"><h1>&Iacute;ndice</h1><div id="toc-body"></div></section>
<main id="content"></main>
<script id="src" type="text/plain">{md.replace('</script>', '<\\/script>')}</script>
<script src="marked.js"></script>
<script src="katex.js"></script>
<script src="mermaid.js"></script>
<script src="render.js"></script>
</body></html>
"""
(BUILD / "index.html").write_text(html, encoding="utf-8")
print("index.html generado ·", len(md.split()), "palabras")
