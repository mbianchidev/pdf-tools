import {
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import CompressPage from './CompressPage';

const jobState = vi.hoisted(() => ({
  job: null,
  running: false,
  connectionError: false,
  start: vi.fn(),
  cancel: vi.fn(),
  reset: vi.fn(),
}));

const jobServiceMock = vi.hoisted(() => ({
  getDownloadUrl: vi.fn(),
}));

vi.mock('../features/jobs/usePdfJob', () => ({
  usePdfJob: () => jobState,
}));

vi.mock('../services/jobService', () => ({
  jobService: jobServiceMock,
  getApiErrorMessage: (error, fallback) => error.message || fallback,
}));

beforeEach(() => {
  jobState.job = null;
  jobState.running = false;
  jobState.connectionError = false;
  jobState.start.mockReset().mockResolvedValue({
    id: 'compress-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/compress-job/outputs/output-1',
  );
});

test('submits the selected compression mode', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File([new Uint8Array(1_000)], 'report.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.selectOptions(
    screen.getByLabelText('Compression mode'),
    'extreme',
  );
  await user.click(screen.getByRole('button', {
    name: 'Compress PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'compress',
    [pdf],
    { mode: 'extreme' },
  );
});

test('shows the original and compressed size comparison', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File([new Uint8Array(1_000)], 'report.pdf', {
      type: 'application/pdf',
    }),
  );
  jobState.job = {
    id: 'compress-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'report-compressed.pdf',
      sizeBytes: 600,
      downloadUrl: '/api/v1/jobs/compress-job/outputs/output-1',
    }],
  };
  rendered.rerender(page());

  expect(screen.getByText('40% smaller')).toBeVisible();
  expect(screen.getByText('1,000 B')).toBeVisible();
  expect(screen.getByText('600 B')).toBeVisible();
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  click.mockRestore();
});

test('explains lossless and lossy mode tradeoffs', () => {
  renderPage();

  expect(screen.getByText(/lossless structural rewrite/i)).toBeVisible();
  expect(screen.getByText(/text and vector content stay editable/i))
    .toBeVisible();
  expect(screen.getByText(/exact original is returned/i)).toBeVisible();
});

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <CompressPage />
  </MemoryRouter>
);
