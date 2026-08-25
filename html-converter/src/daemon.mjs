import { spawn } from 'node:child_process';
import {
  closeSync,
  constants as fsConstants,
  openSync,
} from 'node:fs';
import {
  copyFile,
  lstat,
  mkdir,
  readFile,
  readdir,
  rm,
} from 'node:fs/promises';
import path from 'node:path';
import {
  isRegularFile,
  marker,
  protocol,
  readRequest,
  writeFailure,
  writeProgress,
} from './protocol.mjs';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const REQUEST_ROOT = environmentPath(
  'HTML_QUEUE_REQUEST_ROOT',
  '/var/lib/pdf-tools-html/requests',
);
const RESPONSE_ROOT = environmentPath(
  'HTML_QUEUE_RESPONSE_ROOT',
  '/var/lib/pdf-tools-html/responses',
);
const SIGNAL_ROOT = environmentPath(
  'HTML_QUEUE_SIGNAL_ROOT',
  '/var/lib/pdf-tools-html/signals',
);
const WORK_ROOT = environmentPath(
  'HTML_SIDECAR_WORK_ROOT',
  '/tmp/html-work',
);
const MAX_INPUT_BYTES = integerEnvironment(
  'HTML_MAX_INPUT_BYTES',
  10 * 1024 * 1024,
);
const MAX_OUTPUT_BYTES = integerEnvironment(
  'HTML_MAX_OUTPUT_BYTES',
  64 * 1024 * 1024,
);
const MAX_LOG_BYTES = integerEnvironment(
  'HTML_MAX_LOG_BYTES',
  1024 * 1024,
);
const WALL_TIMEOUT_MS = durationEnvironment(
  'HTML_WALL_TIMEOUT',
  60_000,
);
const RETENTION_MS = durationEnvironment(
  'HTML_QUEUE_RETENTION',
  60 * 60 * 1000,
);
const CONVERTER = new URL('./converter.mjs', import.meta.url);

let stopping = false;
let activeProcess = null;
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    stopping = true;
  });
}

const main = async () => {
  await Promise.all([
    requireDirectory(REQUEST_ROOT),
    requireDirectory(RESPONSE_ROOT),
    requireDirectory(SIGNAL_ROOT),
    requireDirectory(WORK_ROOT),
  ]);
  await recover();
  await marker(path.join(RESPONSE_ROOT, protocol.daemonReady));

  while (!stopping) {
    await cleanupResponses();
    const processed = await processNext();
    if (!processed) await sleep(100);
  }
  await terminateGroup(activeProcess);
};

const processNext = async () => {
  const entries = (await readdir(REQUEST_ROOT, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory() && UUID.test(entry.name))
    .sort((left, right) => left.name.localeCompare(right.name));
  for (const entry of entries) {
    const requestId = entry.name;
    const requestDirectory = path.join(REQUEST_ROOT, requestId);
    if (!await isRegularFile(path.join(requestDirectory, protocol.ready))
        || await signalExists(requestId, protocol.cancel)
        || await signalExists(requestId, protocol.abandoned)) {
      continue;
    }
    const responseDirectory = path.join(RESPONSE_ROOT, requestId);
    try {
      await mkdir(responseDirectory, { mode: 0o700 });
    } catch (error) {
      if (error?.code === 'EEXIST') continue;
      throw error;
    }
    if (await signalExists(requestId, protocol.cancel)
        || await signalExists(requestId, protocol.abandoned)) {
      await rm(responseDirectory, { recursive: true, force: true });
      continue;
    }
    await marker(path.join(responseDirectory, protocol.running));
    await processRequest(requestId, requestDirectory, responseDirectory);
    return true;
  }
  return false;
};

const processRequest = async (
  requestId,
  requestDirectory,
  responseDirectory,
) => {
  const work = path.join(WORK_ROOT, requestId);
  let child;
  let stdoutFd;
  let stderrFd;
  try {
    const request = await readRequest(
      path.join(requestDirectory, protocol.request),
    );
    const source = path.join(
      requestDirectory,
      protocol.input + request.extension,
    );
    const sourceMetadata = await lstat(source);
    if (!sourceMetadata.isFile() || sourceMetadata.isSymbolicLink()
        || sourceMetadata.size < 1
        || sourceMetadata.size > MAX_INPUT_BYTES) {
      throw codedError(
        'INVALID_HTML_DOCUMENT',
        'The queued HTML document is invalid',
      );
    }
    await mkdir(work, { mode: 0o700 });
    const output = path.join(work, protocol.output);
    const errorFile = path.join(work, 'worker-error.json');
    const stdout = path.join(work, 'stdout.log');
    const stderr = path.join(work, 'stderr.log');
    stdoutFd = openSync(
      stdout,
      fsConstants.O_CREAT | fsConstants.O_EXCL | fsConstants.O_WRONLY,
      0o600,
    );
    stderrFd = openSync(
      stderr,
      fsConstants.O_CREAT | fsConstants.O_EXCL | fsConstants.O_WRONLY,
      0o600,
    );
    child = spawn(
      process.execPath,
      [
        CONVERTER.pathname,
        source,
        output,
        errorFile,
        JSON.stringify(request.options),
      ],
      {
        cwd: work,
        detached: true,
        env: workerEnvironment(work),
        stdio: ['ignore', stdoutFd, stderrFd],
      },
    );
    activeProcess = child;
    closeSync(stdoutFd);
    closeSync(stderrFd);
    stdoutFd = null;
    stderrFd = null;
    await monitor(
      child,
      requestId,
      responseDirectory,
      stdout,
      stderr,
    );
    activeProcess = null;
    if (child.exitCode !== 0) {
      const failure = await readWorkerFailure(errorFile);
      await logWorkerFailure(child.exitCode, stderr);
      throw codedError(failure.code, failure.message);
    }
    if (groupAlive(child.pid)) {
      await terminateGroup(child);
      throw codedError(
        'HTML_RESIDUAL_PROCESS',
        'Chromium left an unexpected process running',
      );
    }
    const outputMetadata = await lstat(output);
    if (!outputMetadata.isFile() || outputMetadata.isSymbolicLink()
        || outputMetadata.size < 1
        || outputMetadata.size > MAX_OUTPUT_BYTES) {
      throw codedError(
        'INVALID_HTML_PDF_OUTPUT',
        'Chromium returned an unreadable PDF',
      );
    }
    await copyFile(
      output,
      path.join(responseDirectory, protocol.output),
      fsConstants.COPYFILE_EXCL,
    );
    await marker(path.join(responseDirectory, protocol.completed));
  } catch (error) {
    await terminateGroup(child);
    activeProcess = null;
    const cancelled = stopping
      || await signalExists(requestId, protocol.cancel)
      || await signalExists(requestId, protocol.abandoned);
    const code = safeCode(error?.code);
    await writeFailure(
      path.join(responseDirectory, protocol.failed),
      cancelled
        ? 'HTML_CONVERSION_CANCELLED'
        : code,
      cancelled
        ? 'HTML conversion was cancelled'
        : code === 'HTML_CONVERSION_FAILED'
          ? 'The isolated HTML converter failed'
          : safeMessage(error?.message),
    );
  } finally {
    if (stdoutFd !== null && stdoutFd !== undefined) closeSync(stdoutFd);
    if (stderrFd !== null && stderrFd !== undefined) closeSync(stderrFd);
    await rm(work, { recursive: true, force: true });
  }
};

const monitor = async (
  child,
  requestId,
  responseDirectory,
  stdout,
  stderr,
) => {
  const started = Date.now();
  let reported = 3;
  while (child.exitCode === null && child.signalCode === null) {
    if (stopping
        || await signalExists(requestId, protocol.cancel)
        || await signalExists(requestId, protocol.abandoned)) {
      throw codedError(
        'HTML_CONVERSION_CANCELLED',
        'HTML conversion was cancelled',
      );
    }
    if (Date.now() - started >= WALL_TIMEOUT_MS) {
      throw codedError(
        'HTML_CONVERSION_TIMEOUT',
        'HTML conversion exceeded the configured time limit',
      );
    }
    if (await fileTooLarge(stdout) || await fileTooLarge(stderr)) {
      throw codedError(
        'HTML_CONVERTER_LOG_LIMIT_EXCEEDED',
        'Chromium exceeded the configured log limit',
      );
    }
    const next = Math.min(
      90,
      3 + Math.floor(87 * (Date.now() - started) / WALL_TIMEOUT_MS),
    );
    if (next > reported) {
      reported = next;
      await writeProgress(
        path.join(responseDirectory, protocol.progress),
        next,
      );
    }
    await sleep(100);
  }
};

const terminateGroup = async (child) => {
  if (!child?.pid || !groupAlive(child.pid)) return;
  signalGroup(child.pid, 'SIGTERM');
  const deadline = Date.now() + 2000;
  while (Date.now() < deadline && groupAlive(child.pid)) {
    await sleep(50);
  }
  if (groupAlive(child.pid)) {
    signalGroup(child.pid, 'SIGKILL');
    await sleep(100);
  }
};

const cleanupResponses = async () => {
  const entries = await readdir(RESPONSE_ROOT, { withFileTypes: true });
  const cutoff = Date.now() - RETENTION_MS;
  for (const entry of entries) {
    if (!entry.isDirectory() || !UUID.test(entry.name)) continue;
    const response = path.join(RESPONSE_ROOT, entry.name);
    if (await signalExists(entry.name, protocol.acknowledged)) {
      await rm(response, { recursive: true, force: true });
      continue;
    }
    const metadata = await lstat(response);
    if (metadata.mtimeMs < cutoff) {
      await rm(response, { recursive: true, force: true });
    }
  }
};

const recover = async () => {
  for (const entry of await readdir(WORK_ROOT, { withFileTypes: true })) {
    await rm(path.join(WORK_ROOT, entry.name), {
      recursive: true,
      force: true,
    });
  }
  for (const entry of await readdir(
    RESPONSE_ROOT,
    { withFileTypes: true },
  )) {
    if (!entry.isDirectory() || !UUID.test(entry.name)) continue;
    const response = path.join(RESPONSE_ROOT, entry.name);
    const terminal = await isRegularFile(
      path.join(response, protocol.completed),
    ) || await isRegularFile(path.join(response, protocol.failed));
    if (!terminal) {
      await rm(response, { recursive: true, force: true });
    }
  }
  await rm(
    path.join(RESPONSE_ROOT, protocol.daemonReady),
    { force: true },
  );
};

const readWorkerFailure = async (errorFile) => {
  try {
    const metadata = await lstat(errorFile);
    if (!metadata.isFile() || metadata.isSymbolicLink()
        || metadata.size < 2 || metadata.size > 4096) {
      throw new Error('invalid worker error');
    }
    const parsed = JSON.parse(await readFile(errorFile, 'utf8'));
    return {
      code: safeCode(parsed?.code),
      message: safeMessage(parsed?.message),
    };
  } catch {
    return {
      code: 'HTML_RENDER_FAILED',
      message: 'Chromium could not render the HTML document',
    };
  }
};

const logWorkerFailure = async (exitCode, stderr) => {
  try {
    const metadata = await lstat(stderr);
    if (!metadata.isFile() || metadata.isSymbolicLink()) return;
    const bytes = await readFile(stderr);
    const tail = bytes.subarray(Math.max(0, bytes.length - 4096))
      .toString('utf8');
    console.error(`HTML renderer exited ${exitCode}: ${tail}`);
  } catch {
    console.error(`HTML renderer exited ${exitCode}; stderr unavailable`);
  }
};

const workerEnvironment = (work) => ({
  HOME: work,
  TMPDIR: work,
  XDG_CACHE_HOME: work,
  XDG_CONFIG_HOME: work,
  LANG: 'C.UTF-8',
  LC_ALL: 'C.UTF-8',
  NODE_ENV: 'production',
  PATH: '/usr/local/bin:/usr/bin:/bin',
  PLAYWRIGHT_BROWSERS_PATH:
    process.env.PLAYWRIGHT_BROWSERS_PATH ?? '/ms-playwright',
  HTML_BROWSER_TIMEOUT_MS: String(Math.max(WALL_TIMEOUT_MS - 5000, 1000)),
});

const signalExists = (requestId, suffix) => isRegularFile(
  path.join(SIGNAL_ROOT, requestId + suffix),
);

const fileTooLarge = async (filePath) => {
  try {
    return (await lstat(filePath)).size > MAX_LOG_BYTES;
  } catch (error) {
    if (error?.code === 'ENOENT') return false;
    throw error;
  }
};

const requireDirectory = async (directory) => {
  const metadata = await lstat(directory);
  if (!metadata.isDirectory() || metadata.isSymbolicLink()) {
    throw new Error(`HTML queue root is unavailable: ${directory}`);
  }
};

const groupAlive = (pid) => {
  try {
    process.kill(-pid, 0);
    return true;
  } catch (error) {
    if (error?.code === 'ESRCH') return false;
    throw error;
  }
};

const signalGroup = (pid, signal) => {
  try {
    process.kill(-pid, signal);
  } catch (error) {
    if (error?.code !== 'ESRCH') throw error;
  }
};

const safeCode = (code) => (
  typeof code === 'string' && /^HTML_[A-Z0-9_]{1,91}$/.test(code)
    ? code
    : 'HTML_CONVERSION_FAILED'
);

const safeMessage = (message) => (
  typeof message === 'string' && message.length > 0
    ? message.replace(/[^\x20-\x7E]/g, '?').slice(0, 1000)
    : 'The isolated HTML converter failed'
);

const codedError = (code, message) => Object.assign(
  new Error(message),
  { code },
);

function environmentPath(name, fallback) {
  const value = process.env[name] ?? fallback;
  if (!path.isAbsolute(value)) {
    throw new Error(`${name} must be absolute`);
  }
  return path.normalize(value);
}

function integerEnvironment(name, fallback) {
  const value = Number.parseInt(process.env[name] ?? String(fallback), 10);
  if (!Number.isSafeInteger(value) || value < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

function durationEnvironment(name, fallback) {
  const value = process.env[name];
  if (!value) return fallback;
  const match = /^([1-9]\d*)(ms|s|m|h)$/.exec(value);
  if (!match) throw new Error(`${name} has an invalid duration`);
  const multiplier = {
    ms: 1,
    s: 1000,
    m: 60_000,
    h: 3_600_000,
  }[match[2]];
  return Number.parseInt(match[1], 10) * multiplier;
}

const sleep = (milliseconds) => new Promise(
  (resolve) => setTimeout(resolve, milliseconds),
);

await main();
