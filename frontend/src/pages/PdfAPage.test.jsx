import {
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PdfAPage from './PdfAPage';

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
    id: 'pdfa-job',
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
      status: 'compliant',
      profile: 'pdfa-3b',
      compliant: true,
      totalAssertions: 142,
      failedChecks: 0,
      failures: [],
    }),
  ], { type: 'application/json' }));
});

test('submits the selected PDF/A profile', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'report.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.selectOptions(
    screen.getByLabelText('PDF/A profile'),
    'pdfa-3b',
  );
  await user.click(screen.getByRole('button', {
    name: 'Convert to PDF/A',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'pdf-to-pdfa',
    [pdf],
    { profile: 'pdfa-3b' },
  );
});

test('shows the independent veraPDF result', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File(['pdf'], 'report.pdf', {
      type: 'application/pdf',
    }),
  );
  jobState.job = completedJob();
  rendered.rerender(page());

  const result = (await screen.findByText('veraPDF compliant'))
    .closest('.pdfa-result');
  expect(result).not.toBeNull();
  expect(within(result).getByText('PDF/A-3b')).toBeVisible();
  expect(within(result).getByText('142 assertions checked')).toBeVisible();
  expect(jobServiceMock.download).toHaveBeenCalledWith(
    jobState.job.outputs[1],
  );
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  click.mockRestore();
});

test('explains converter and validator fidelity boundaries', () => {
  renderPage();

  expect(screen.getByText(/LibreOffice Draw reimports the PDF/i))
    .toBeVisible();
  expect(screen.getByText(/conformance does not guarantee visual parity/i))
    .toBeVisible();
  expect(screen.getByText(/isolated veraPDF worker/i)).toBeVisible();
});

const completedJob = () => ({
  id: 'pdfa-job',
  status: 'COMPLETED',
  version: 2,
  progress: 100,
  message: 'Completed',
  outputs: [{
    id: 'pdf-output',
    filename: 'report-pdfa-3b.pdf',
    mediaType: 'application/pdf',
    downloadUrl: '/api/v1/jobs/pdfa-job/outputs/pdf-output',
  }, {
    id: 'report-output',
    filename: 'report-pdfa-3b-validation-report.json',
    mediaType: 'application/json',
    downloadUrl: '/api/v1/jobs/pdfa-job/outputs/report-output',
  }],
});

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <PdfAPage />
  </MemoryRouter>
);
