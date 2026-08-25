import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PdfToWordPage from './PdfToWordPage';

const jobState = vi.hoisted(() => ({
  job: null,
  running: false,
  connectionError: false,
  start: vi.fn(),
  cancel: vi.fn(),
  reset: vi.fn(),
}));

vi.mock('../features/jobs/usePdfJob', () => ({
  usePdfJob: () => jobState,
}));

vi.mock('../services/jobService', () => ({
  jobService: { getDownloadUrl: vi.fn() },
  getApiErrorMessage: (error, fallback) => error.message || fallback,
}));

beforeEach(() => {
  jobState.job = null;
  jobState.running = false;
  jobState.connectionError = false;
  jobState.start.mockReset().mockResolvedValue({
    id: 'word-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset();
  jobState.reset.mockReset();
});

test('submits editable extraction controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'report.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByLabelText('Include embedded images'));
  await user.click(screen.getByRole('button', {
    name: 'Convert PDF to Word',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'pdf-to-word',
    [pdf],
    {
      mode: 'editable',
      includeImages: false,
      detectTables: true,
      preservePageBreaks: true,
    },
  );
});

test('explains visual mode and removes irrelevant controls', async () => {
  const user = userEvent.setup();
  renderPage();

  await user.selectOptions(
    screen.getByLabelText('Conversion mode'),
    'visual',
  );

  expect(screen.queryByLabelText('Include embedded images'))
    .not.toBeInTheDocument();
  expect(screen.queryByLabelText('Detect aligned tables'))
    .not.toBeInTheDocument();
  expect(screen.getByText(/page contents are not editable/i)).toBeVisible();
});

test('states editable fidelity limits and worker bounds', () => {
  renderPage();

  expect(screen.getByText(/complex columns, vector art/i)).toBeVisible();
  expect(screen.getByText(/killable Java worker/i)).toBeVisible();
});

const renderPage = () => render(
  <MemoryRouter>
    <PdfToWordPage />
  </MemoryRouter>,
);
