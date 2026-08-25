import {
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import RepairPage from './RepairPage';

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
  download: vi.fn(),
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
    id: 'repair-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockImplementation(
    (output) => `http://localhost${output.downloadUrl}`,
  );
  jobServiceMock.download.mockReset().mockResolvedValue(new Blob([
    JSON.stringify({
      status: 'partially-recovered',
      summary: 'qpdf recovered the PDF with warnings',
      recoveredPages: 3,
      warnings: ['Cross-reference table was reconstructed'],
    }),
  ], { type: 'application/json' }));
});

test('submits one PDF to the repair job', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'broken.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByRole('button', {
    name: 'Repair PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith('repair', [pdf], {});
});

test('surfaces partial recovery warnings from the report', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File(['pdf'], 'broken.pdf', {
      type: 'application/pdf',
    }),
  );
  jobState.job = completedJob();
  rendered.rerender(page());

  expect(await screen.findByText('Partially recovered')).toBeVisible();
  expect(screen.getByText('Cross-reference table was reconstructed'))
    .toBeVisible();
  expect(screen.getByText('3 pages recovered')).toBeVisible();
  expect(jobServiceMock.download).toHaveBeenCalledWith(
    jobState.job.outputs[1],
  );
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  click.mockRestore();
});

test('explains repair limitations and report behavior', () => {
  renderPage();

  expect(screen.getByText(/cannot recreate missing page content/i))
    .toBeVisible();
  expect(screen.getByText(/always includes a JSON repair report/i))
    .toBeVisible();
  expect(screen.getByText(
    /qpdf runs as a non-root, network-denied process/i,
  )).toBeVisible();
});

const completedJob = () => ({
  id: 'repair-job',
  status: 'COMPLETED',
  version: 2,
  progress: 100,
  message: 'Completed',
  outputs: [{
    id: 'pdf-output',
    filename: 'broken-repaired.pdf',
    mediaType: 'application/pdf',
    downloadUrl: '/api/v1/jobs/repair-job/outputs/pdf-output',
  }, {
    id: 'report-output',
    filename: 'broken-repair-report.json',
    mediaType: 'application/json',
    downloadUrl: '/api/v1/jobs/repair-job/outputs/report-output',
  }],
});

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <RepairPage />
  </MemoryRouter>
);
