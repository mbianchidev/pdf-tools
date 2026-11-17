import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import {
  mkdtemp,
  readFile,
  rm,
  writeFile,
} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import test from 'node:test';
import { convert } from '../src/converter.mjs';

const execute = promisify(execFile);
const enabled = process.env.HTML_BROWSER_INTEGRATION === '1';

test('typesets Markdown math with embedded KaTeX fonts', {
  skip: !enabled,
  timeout: 60_000,
}, async () => {
  const directory = await mkdtemp(
    path.join(os.tmpdir(), 'html-math-integration-'),
  );
  try {
    const source = path.join(directory, 'math.html');
    const output = path.join(directory, 'math.pdf');
    await writeFile(source, `<!doctype html><html><head>
      <meta http-equiv="Content-Security-Policy"
        content="default-src 'none'; font-src data:; style-src 'unsafe-inline'">
      </head><body>
      <p>Inline <span class="math math-inline">x^2 &lt; y</span></p>
      <p><span class="math math-display">\\int_0^1 f(x)\\,dx</span></p>
      </body></html>`);

    await convert({
      sourcePath: source,
      outputPath: output,
      plan: {
        pageSize: 'A4',
        landscape: false,
        printBackground: true,
        marginMm: 10,
        renderMath: true,
      },
      timeoutMs: 45_000,
      chromiumSandbox: false,
    });

    const { stdout: fonts } = await execute('pdffonts', [output]);
    assert.match(fonts, /KaTeX_Main/);
    assert.match(fonts, /KaTeX_Size\d/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('renders the HTML golden fixture', {
  skip: !enabled,
  timeout: 60_000,
}, async () => {
  const directory = await mkdtemp(
    path.join(os.tmpdir(), 'html-browser-integration-'),
  );
  try {
    const source = path.join(directory, 'fixture.html');
    const output = path.join(directory, 'fixture.pdf');
    await writeFile(source, `<!doctype html>
      <html>
        <head>
          <style>
            body {
              background: rgb(51, 68, 170);
              color: white;
              font: 24px sans-serif;
            }
          </style>
        </head>
        <body>
          <h1>HTML GOLDEN</h1>
          <p id="status">waiting</p>
          <iframe src="file:///etc/passwd"></iframe>
          <img src="https://example.com/tracker.png">
          <script>
            document.getElementById('status').textContent = 'SCRIPT RAN';
          </script>
        </body>
      </html>`);

    await convert({
      sourcePath: source,
      outputPath: output,
      plan: {
        pageSize: 'Letter',
        landscape: true,
        printBackground: true,
        marginMm: 12,
      },
      timeoutMs: 45_000,
      chromiumSandbox: false,
    });

    const pdf = await readFile(output);
    assert.equal(pdf.subarray(0, 5).toString('ascii'), '%PDF-');
    const { stdout: text } = await execute('pdftotext', [output, '-']);
    assert.match(text, /HTML GOLDEN/);
    assert.match(text, /SCRIPT RAN/);
    assert.doesNotMatch(text, /root:/);

    const { stdout: information } = await execute('pdfinfo', [output]);
    const size = /^Page size:\s+([\d.]+) x ([\d.]+)/m.exec(information);
    assert.ok(size);
    assert.ok(Number(size[1]) > Number(size[2]));

    const rasterPrefix = path.join(directory, 'fixture');
    await execute('pdftoppm', [
      '-f', '1',
      '-l', '1',
      '-singlefile',
      '-r', '36',
      output,
      rasterPrefix,
    ]);
    const raster = await readPpm(`${rasterPrefix}.ppm`);
    const center = raster.pixel(
      Math.floor(raster.width / 2),
      Math.floor(raster.height / 2),
    );
    assert.ok(center.blue > center.red);
    assert.ok(center.blue > center.green);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

const readPpm = async (file) => {
  const bytes = await readFile(file);
  let offset = 0;
  const token = () => {
    while (offset < bytes.length && /\s/.test(
      String.fromCharCode(bytes[offset]),
    )) offset++;
    if (bytes[offset] === 35) {
      while (offset < bytes.length && bytes[offset++] !== 10) {
      }
      return token();
    }
    const start = offset;
    while (offset < bytes.length && !/\s/.test(
      String.fromCharCode(bytes[offset]),
    )) offset++;
    return bytes.subarray(start, offset).toString('ascii');
  };
  assert.equal(token(), 'P6');
  const width = Number.parseInt(token(), 10);
  const height = Number.parseInt(token(), 10);
  assert.equal(Number.parseInt(token(), 10), 255);
  while (offset < bytes.length && /\s/.test(
    String.fromCharCode(bytes[offset]),
  )) offset++;
  return {
    width,
    height,
    pixel: (x, y) => {
      const index = offset + (y * width + x) * 3;
      return {
        red: bytes[index],
        green: bytes[index + 1],
        blue: bytes[index + 2],
      };
    },
  };
};
