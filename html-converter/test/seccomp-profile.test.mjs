import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const profile = JSON.parse(await readFile(
  new URL('../seccomp_profile.json', import.meta.url),
  'utf8',
));

test('keeps Chromium namespace calls and libc fallbacks explicit', () => {
  assert.equal(profile.defaultAction, 'SCMP_ACT_ERRNO');
  const clone3 = profile.syscalls.find(
    (rule) => rule.names.includes('clone3'),
  );
  assert.equal(clone3.action, 'SCMP_ACT_ERRNO');
  assert.equal(clone3.errnoRet, 38);

  const unconditional = new Set(profile.syscalls
    .filter((rule) => rule.action === 'SCMP_ACT_ALLOW'
      && Object.keys(rule.includes ?? {}).length === 0
      && Object.keys(rule.excludes ?? {}).length === 0)
    .flatMap((rule) => rule.names));
  for (const syscall of [
    'clone',
    'setns',
    'unshare',
    'close_range',
    'faccessat2',
  ]) {
    assert.equal(unconditional.has(syscall), true, syscall);
  }
  for (const privileged of ['bpf', 'mount', 'open_by_handle_at']) {
    assert.equal(unconditional.has(privileged), false, privileged);
  }
});
