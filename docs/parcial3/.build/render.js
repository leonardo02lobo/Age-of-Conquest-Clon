(function () {
  var md = document.getElementById('src').textContent;
  var slots = [];
  function stash(kind, code) { slots.push({ k: kind, c: code }); return '@@S' + (slots.length - 1) + '@@'; }

  // 1. bloques mermaid   2. resto de bloques de código   3. $$…$$   4. $…$
  md = md.replace(/```mermaid\r?\n([\s\S]*?)```/g, function (_, c) { return stash('mermaid', c); });
  md = md.replace(/```[a-zA-Z0-9]*\r?\n([\s\S]*?)```/g, function (_, c) { return stash('code', c); });
  md = md.replace(/\$\$([\s\S]*?)\$\$/g, function (_, c) { return stash('display', c); });
  md = md.replace(/\$([^$\n]+?)\$/g, function (_, c) { return stash('inline', c); });

  marked.setOptions({ gfm: true, breaks: false });
  var html = marked.parse(md);

  function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

  html = html.replace(/@@S(\d+)@@/g, function (_, i) {
    var s = slots[+i];
    try {
      if (s.k === 'display') return katex.renderToString(s.c, { displayMode: true, throwOnError: false, strict: false });
      if (s.k === 'inline') return katex.renderToString(s.c, { displayMode: false, throwOnError: false, strict: false });
    } catch (e) { return '<code class="mathfail">' + esc(s.c) + '</code>'; }
    if (s.k === 'mermaid') return '<div class="mermaid-wrap"><pre class="mermaid">' + esc(s.c) + '</pre></div>';
    return '<pre><code>' + esc(s.c) + '</code></pre>';
  });

  document.getElementById('content').innerHTML = html;

  // tablas anchas -> contenedor con scroll/ajuste
  document.querySelectorAll('#content table').forEach(function (t) {
    var w = document.createElement('div'); w.className = 'tablewrap';
    t.parentNode.insertBefore(w, t); w.appendChild(t);
  });

  // índice a partir de h1/h2
  var toc = '', n1 = 0;
  document.querySelectorAll('#content h1, #content h2').forEach(function (h, i) {
    h.id = 'h' + i;
    var lvl = h.tagName === 'H1' ? 1 : 2;
    if (lvl === 1) { n1++; toc += '<p class="toc1"><a href="#h' + i + '">' + h.textContent + '</a></p>'; }
    else if (n1 > 0) { toc += '<p class="toc2"><a href="#h' + i + '">' + h.textContent + '</a></p>'; }
  });
  document.getElementById('toc-body').innerHTML = toc;

  mermaid.initialize({
    startOnLoad: false, theme: 'neutral', securityLevel: 'loose',
    flowchart: { htmlLabels: true, curve: 'basis', useMaxWidth: true },
    themeVariables: { fontFamily: 'Georgia, serif', fontSize: '13px' }
  });
  mermaid.run({ querySelector: '.mermaid' })
    .then(function () { document.body.setAttribute('data-ready', '1'); })
    .catch(function (e) { console.error(e); document.body.setAttribute('data-ready', '1'); });
})();
