import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import katex from 'katex';

const require = createRequire(import.meta.url);
const MATH_SPAN = /<span class="math math-(inline|display)">([^<]*)<\/span>/g;
const KATEX_OPTIONS = Object.freeze({
  throwOnError: false,
  trust: false,
  strict: 'ignore',
  maxSize: 50,
  maxExpand: 1000,
  output: 'htmlAndMathml',
});
const ENTITIES = Object.freeze({
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
});

export const decodeEntities = (text) => text.replace(
  /&(?:#(\d{1,7})|#x([\da-f]{1,6})|(amp|lt|gt|quot|apos));/gi,
  (entity, decimal, hex, name) => {
    if (name) return ENTITIES[name.toLowerCase()];
    const codePoint = Number.parseInt(decimal ?? hex, decimal ? 10 : 16);
    return codePoint > 0 && codePoint <= 0x10ffff
      ? String.fromCodePoint(codePoint)
      : entity;
  },
);

let stylesheet;

/** KaTeX CSS with its WOFF2 fonts inlined, since pages cannot fetch files. */
export const katexStylesheet = () => {
  if (stylesheet) return stylesheet;
  const cssPath = require.resolve('katex/dist/katex.min.css');
  const fontDirectory = path.join(path.dirname(cssPath), 'fonts');
  stylesheet = readFileSync(cssPath, 'utf8').replace(
    /src:[^;}]*/g,
    (sources) => {
      const font = /url\(fonts\/([\w-]+\.woff2)\)/.exec(sources)?.[1];
      if (!font) return sources;
      const data = readFileSync(path.join(fontDirectory, font))
        .toString('base64');
      return `src:url(data:font/woff2;base64,${data}) format("woff2")`;
    },
  );
  return stylesheet;
};

/**
 * Typesets Markdown to PDF math marker spans with KaTeX. Untrusted TeX runs
 * with trust disabled, so commands such as \href cannot add links or HTML.
 */
export const renderMath = (html) => {
  let found = false;
  const typeset = html.replace(MATH_SPAN, (_, mode, tex) => {
    found = true;
    return katex.renderToString(decodeEntities(tex), {
      ...KATEX_OPTIONS,
      displayMode: mode === 'display',
    });
  });
  if (!found) return html;
  const style = `<style>${katexStylesheet()}</style>`;
  return /<\/head>/i.test(typeset)
    ? typeset.replace(/<\/head>/i, `${style}</head>`)
    : `${style}${typeset}`;
};
