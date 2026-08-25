import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { jobService } from '../services/jobService';
import MergePage from './MergePage';

const jobState = vi.hoisted(() => ({
  job: null,
  running: false,
  connectionError: false,
  start: vi.fn(),
  cancel: vi.fn(),
  reset: vi.fn(),
}));

vi.mock('react-pdf', () => ({
  Document: ({ children }) => <div>{children}</div>,
  Page: () => <div>PDF preview</div>,
  pdfjs: {
    GlobalWorkerOptions: {},
  },
}));

vi.mock('../features/jobs/usePdfJob', () => ({
  usePdfJob: () => jobState,
}));

vi.mock('../services/jobService', () => ({
  jobService: {
    download: vi.fn(),
  },
  getApiErrorMessage: (error, fallback) => error.message || fallback,
}));

vi.mock('../services/pdfService', () => ({
  downloadBlob: vi.fn(),
}));

beforeEach(() => {
  jobState.job = null;
  jobState.running = false;
  jobState.connectionError = false;
  jobState.start.mockReset().mockResolvedValue({
    id: 'merge-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobService.download.mockReset();
});

test('submits files in the visible deterministic order', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <MergePage />
    </MemoryRouter>,
  );
  const first = new File(['first'], 'first.pdf', { type: 'application/pdf' });
  const second = new File(['second'], 'second.pdf', { type: 'application/pdf' });

  await user.upload(container.querySelector('input[type="file"]'), [first, second]);
  await user.click(await screen.findByRole('button', { name: 'Move second.pdf up' }));
  await user.click(screen.getByRole('button', { name: 'Merge & Download' }));

  await waitFor(() => expect(jobState.start).toHaveBeenCalledOnce());
  const [operation, files, options] = jobState.start.mock.calls[0];
  expect(operation).toBe('merge');
  expect(files.map((file) => file.name)).toEqual(['second.pdf', 'first.pdf']);
  expect(options).toEqual({});
});

test('exposes cancellation while a merge is running', async () => {
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <MergePage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    [
      new File(['first'], 'first.pdf', { type: 'application/pdf' }),
      new File(['second'], 'second.pdf', { type: 'application/pdf' }),
    ],
  );

  jobState.job = {
    id: 'merge-job',
    status: 'RUNNING',
    version: 1,
    progress: 40,
    message: 'Processing',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <MergePage />
    </MemoryRouter>,
  );

  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();
});

test('offers a retry when a completed output download fails', async () => {
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <MergePage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    [
      new File(['first'], 'first.pdf', { type: 'application/pdf' }),
      new File(['second'], 'second.pdf', { type: 'application/pdf' }),
    ],
  );
  jobService.download
    .mockRejectedValueOnce(new Error('Network interrupted'))
    .mockResolvedValueOnce(new Blob(['merged']));
  jobState.job = {
    id: 'merge-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'first_merged.pdf',
      downloadUrl: '/api/v1/jobs/merge-job/outputs/output-1',
    }],
  };
  rerender(
    <MemoryRouter>
      <MergePage />
    </MemoryRouter>,
  );

  const retry = await screen.findByRole('button', { name: 'Retry download' });
  await user.click(retry);

  await waitFor(() => expect(jobService.download).toHaveBeenCalledTimes(2));
  await waitFor(() => (
    expect(screen.queryByRole('button', { name: 'Retry download' }))
      .not.toBeInTheDocument()
  ));
});
