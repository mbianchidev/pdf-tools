import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeEntities, katexStylesheet, renderMath } from '../src/math.mjs';

test('typesets inline and display math marker spans', () => {
  const html = renderMath(`<html><head><title>T</title></head><body>
    <p><span class="math math-inline">a &lt; b^2</span></p>
    <p><span class="math math-display">\\frac{1}{2}</span></p>
  </body></html>`);

  assert.match(html, /class="katex"/);
  assert.match(html, /class="katex-display"/);
  assert.match(html, /<mo>&lt;<\/mo>/);
  assert.doesNotMatch(html, /class="math math-/);
  assert.ok(html.indexOf('<style>') < html.indexOf('</head>'));
});

test('embeds KaTeX fonts as data URLs only', () => {
  const css = katexStylesheet();

  assert.match(css, /@font-face/);
  assert.match(css, /url\(data:font\/woff2;base64,/);
  assert.doesNotMatch(css, /url\(fonts\//);
});

test('leaves documents without math untouched', () => {
  const html = '<p class="math">$x$</p>';

  assert.equal(renderMath(html), html);
});

test('keeps untrusted commands inert', () => {
  const html = renderMath(
    '<span class="math math-inline">\\href{https://example.com}{x}'
      + '\\htmlClass{evil}{y}</span>',
  );

  assert.doesNotMatch(html, /href="https/);
  assert.doesNotMatch(html, /class="evil"/);
});

test('decodes the escapes produced by the Markdown renderer', () => {
  assert.equal(
    decodeEntities('&amp;&lt;&gt;&quot;&#39;&#x41;&#0;'),
    '&<>"\'A&#0;',
  );
});
