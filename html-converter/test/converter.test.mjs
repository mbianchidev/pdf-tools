import assert from 'node:assert/strict';
import test from 'node:test';
import {
  injectCsp,
  isAllowedResource,
  pdfOptions,
} from '../src/converter.mjs';

test('injects restrictive CSP into a document head', () => {
  const html = injectCsp('<html><head><title>Safe</title></head></html>');

  assert.match(html, /Content-Security-Policy/);
  assert.match(html, /connect-src 'none'/);
  assert.ok(html.indexOf('Content-Security-Policy') < html.indexOf('<title>'));
});

test('allows inline resources but blocks files and networks', () => {
  assert.equal(isAllowedResource('data:image/png;base64,AA=='), true);
  assert.equal(isAllowedResource('blob:null/id'), true);
  assert.equal(isAllowedResource('about:blank'), true);
  assert.equal(isAllowedResource('file:///etc/passwd'), false);
  assert.equal(isAllowedResource('https://example.com/image.png'), false);
});

test('builds bounded PDF controls', () => {
  assert.deepEqual(
    pdfOptions({
      pageSize: 'Letter',
      landscape: true,
      printBackground: false,
      marginMm: 12,
    }, '/tmp/output.pdf'),
    {
      path: '/tmp/output.pdf',
      format: 'Letter',
      landscape: true,
      printBackground: false,
      margin: {
        top: '12mm',
        right: '12mm',
        bottom: '12mm',
        left: '12mm',
      },
      preferCSSPageSize: false,
      displayHeaderFooter: false,
      tagged: true,
      outline: false,
    },
  );
});
