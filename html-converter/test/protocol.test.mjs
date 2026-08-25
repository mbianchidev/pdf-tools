import assert from 'node:assert/strict';
import { mkdtemp, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { readRequest } from '../src/protocol.mjs';

test('reads the Java v2 HTML request layout', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'html-protocol-'));
  const request = path.join(directory, 'request.bin');
  const options = JSON.stringify({
    pageSize: 'A4',
    landscape: false,
    printBackground: true,
    marginMm: 10,
  });
  await writeFile(request, Buffer.concat([
    int32(2),
    utf('html'),
    utf('.html'),
    utf(options),
  ]));

  assert.deepEqual(await readRequest(request), {
    type: 'html',
    extension: '.html',
    options: JSON.parse(options),
  });
});

test('rejects trailing bytes and invalid plans', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'html-protocol-'));
  const trailing = path.join(directory, 'trailing.bin');
  await writeFile(trailing, Buffer.concat([
    int32(2),
    utf('html'),
    utf('.html'),
    utf(JSON.stringify({
      pageSize: 'A4',
      landscape: false,
      printBackground: true,
      marginMm: 10,
    })),
    Buffer.from([1]),
  ]));
  await assert.rejects(() => readRequest(trailing), {
    code: 'HTML_QUEUE_PROTOCOL_ERROR',
  });

  const invalid = path.join(directory, 'invalid.bin');
  await writeFile(invalid, Buffer.concat([
    int32(2),
    utf('html'),
    utf('.html'),
    utf('{"pageSize":"A0"}'),
  ]));
  await assert.rejects(() => readRequest(invalid), {
    code: 'HTML_QUEUE_PROTOCOL_ERROR',
  });
});

const int32 = (value) => {
  const bytes = Buffer.alloc(4);
  bytes.writeInt32BE(value);
  return bytes;
};

const utf = (value) => {
  const encoded = Buffer.from(value);
  const length = Buffer.alloc(2);
  length.writeUInt16BE(encoded.length);
  return Buffer.concat([length, encoded]);
};
