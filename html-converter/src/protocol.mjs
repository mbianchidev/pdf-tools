import {
  constants as fsConstants,
  open,
  lstat,
  readFile,
  rename,
  rm,
} from 'node:fs/promises';
import path from 'node:path';

export const protocol = Object.freeze({
  request: 'request.bin',
  input: 'input',
  output: 'output.pdf',
  ready: '.ready',
  running: '.running',
  cancel: '.cancel',
  acknowledged: '.acknowledged',
  completed: '.completed',
  failed: '.failed',
  progress: '.progress',
  abandoned: '.abandoned',
  daemonReady: '.daemon-ready',
});

const VERSION_1 = 1;
const VERSION_2 = 2;
const MAX_REQUEST_BYTES = 20 * 1024;
const MAX_OPTIONS_BYTES = 16 * 1024;

export const readRequest = async (requestPath) => {
  const metadata = await lstat(requestPath, { bigint: false });
  if (!metadata.isFile() || metadata.isSymbolicLink() || metadata.size < 8
      || metadata.size > MAX_REQUEST_BYTES) {
    throw protocolError();
  }
  const bytes = await readFile(requestPath);
  let offset = 0;
  const readInt = () => {
    if (offset + 4 > bytes.length) throw protocolError();
    const value = bytes.readInt32BE(offset);
    offset += 4;
    return value;
  };
  const readUtf = () => {
    if (offset + 2 > bytes.length) throw protocolError();
    const length = bytes.readUInt16BE(offset);
    offset += 2;
    if (offset + length > bytes.length) throw protocolError();
    const value = bytes.subarray(offset, offset + length).toString('utf8');
    offset += length;
    return value;
  };

  const version = readInt();
  if (version !== VERSION_1 && version !== VERSION_2) {
    throw protocolError();
  }
  const type = readUtf();
  const extension = readUtf();
  const optionsJson = version === VERSION_2 ? readUtf() : '{}';
  if (offset !== bytes.length || type !== 'html'
      || !/^\.html?$/.test(extension)
      || Buffer.byteLength(optionsJson) > MAX_OPTIONS_BYTES) {
    throw protocolError();
  }
  let options;
  try {
    options = JSON.parse(optionsJson);
  } catch {
    throw protocolError();
  }
  validateHtmlOptions(options);
  return { type, extension, options };
};

export const writeFailure = async (failurePath, code, message) => {
  if (!/^[A-Z0-9_]{1,96}$/.test(code)
      || typeof message !== 'string'
      || message.length < 1
      || message.length > 1000
      || Buffer.byteLength(code) > 65535
      || Buffer.byteLength(message) > 65535) {
    throw protocolError();
  }
  await atomicWrite(failurePath, Buffer.concat([
    int32(VERSION_1),
    utf(code),
    utf(message),
  ]));
};

export const writeProgress = async (progressPath, progress) => {
  if (!Number.isInteger(progress) || progress < 0 || progress > 99) {
    throw protocolError();
  }
  await atomicWrite(progressPath, Buffer.from(String(progress)));
};

export const marker = async (markerPath) => {
  await atomicWrite(markerPath, Buffer.from([1]));
};

export const isRegularFile = async (filePath) => {
  try {
    const metadata = await lstat(filePath, { bigint: false });
    return metadata.isFile() && !metadata.isSymbolicLink();
  } catch (error) {
    if (error?.code === 'ENOENT') return false;
    throw error;
  }
};

const validateHtmlOptions = (options) => {
  const valid = options !== null
    && typeof options === 'object'
    && !Array.isArray(options)
    && ['A4', 'Letter', 'Legal'].includes(options.pageSize)
    && typeof options.landscape === 'boolean'
    && typeof options.printBackground === 'boolean'
    && Number.isInteger(options.marginMm)
    && options.marginMm >= 0
    && options.marginMm <= 50;
  if (!valid) throw protocolError();
};

const atomicWrite = async (destination, bytes) => {
  const temporary = path.join(
    path.dirname(destination),
    `.${path.basename(destination)}.${process.pid}.${Date.now()}.tmp`,
  );
  let handle;
  try {
    handle = await open(
      temporary,
      fsConstants.O_CREAT | fsConstants.O_EXCL | fsConstants.O_WRONLY,
      0o600,
    );
    await handle.writeFile(bytes);
    await handle.sync();
    await handle.close();
    handle = null;
    await rename(temporary, destination);
  } finally {
    await handle?.close().catch(() => {});
    await rm(temporary, { force: true }).catch(() => {});
  }
};

const int32 = (value) => {
  const bytes = Buffer.alloc(4);
  bytes.writeInt32BE(value);
  return bytes;
};

const utf = (value) => {
  const encoded = Buffer.from(value, 'utf8');
  const length = Buffer.alloc(2);
  length.writeUInt16BE(encoded.length);
  return Buffer.concat([length, encoded]);
};

const protocolError = () => Object.assign(
  new Error('The HTML converter queue returned invalid state'),
  { code: 'HTML_QUEUE_PROTOCOL_ERROR' },
);
