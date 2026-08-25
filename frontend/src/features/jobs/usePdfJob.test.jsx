import { act, renderHook } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { jobService } from '../../services/jobService';
import { usePdfJob } from './usePdfJob';

afterEach(() => {
  vi.restoreAllMocks();
});

test('ignores a delayed snapshot older than a terminal SSE event', async () => {
  const created = {
    id: 'job-1',
    status: 'RUNNING',
    version: 1,
    progress: 10,
  };
  let onJob;
  let resolveCancellation;
  vi.spyOn(jobService, 'create').mockResolvedValue(created);
  vi.spyOn(jobService, 'subscribe').mockImplementation((jobId, nextJob) => {
    onJob = nextJob;
    return vi.fn();
  });
  vi.spyOn(jobService, 'cancel').mockReturnValue(new Promise((resolve) => {
    resolveCancellation = resolve;
  }));

  const { result } = renderHook(() => usePdfJob());
  await act(async () => {
    await result.current.start('merge', [new File(['pdf'], 'one.pdf')]);
  });

  let cancelRequest;
  act(() => {
    cancelRequest = result.current.cancel();
  });
  act(() => {
    onJob({
      ...created,
      status: 'COMPLETED',
      version: 3,
      progress: 100,
    });
  });
  await act(async () => {
    resolveCancellation({
      ...created,
      status: 'RUNNING',
      version: 2,
      progress: 50,
    });
    await cancelRequest;
  });

  expect(result.current.job.status).toBe('COMPLETED');
  expect(result.current.job.version).toBe(3);
});

test('closes live updates when cancellation returns a terminal snapshot', async () => {
  const close = vi.fn();
  vi.spyOn(jobService, 'create').mockResolvedValue({
    id: 'job-2',
    status: 'RUNNING',
    version: 1,
    progress: 1,
  });
  vi.spyOn(jobService, 'subscribe').mockReturnValue(close);
  vi.spyOn(jobService, 'cancel').mockResolvedValue({
    id: 'job-2',
    status: 'CANCELLED',
    version: 2,
    progress: 1,
  });

  const { result } = renderHook(() => usePdfJob());
  await act(async () => {
    await result.current.start('merge', [new File(['pdf'], 'one.pdf')]);
  });
  await act(async () => {
    await result.current.cancel();
  });

  expect(result.current.job.status).toBe('CANCELLED');
  expect(close).toHaveBeenCalledOnce();
});
