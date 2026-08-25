import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { startBrowserDownload } from './startBrowserDownload';

const NativeURL = URL;
let click;
let revokeObjectURL;

beforeEach(() => {
  class TestURL extends NativeURL {}

  revokeObjectURL = vi.fn();
  TestURL.revokeObjectURL = revokeObjectURL;
  vi.stubGlobal('URL', TestURL);
  click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});

afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.body.replaceChildren();
});

test('revokes a blob URL after starting its download', () => {
  vi.useFakeTimers();

  startBrowserDownload('blob:https://example.com/output', 'output.pdf');

  expect(click).toHaveBeenCalledOnce();
  expect(document.querySelector('a')).not.toBeInTheDocument();
  expect(revokeObjectURL).not.toHaveBeenCalled();

  vi.runAllTimers();

  expect(revokeObjectURL).toHaveBeenCalledWith('blob:https://example.com/output');
});

test('does not revoke a reusable remote URL', () => {
  startBrowserDownload('https://example.com/output.pdf', 'output.pdf');

  expect(click).toHaveBeenCalledOnce();
  expect(revokeObjectURL).not.toHaveBeenCalled();
});
