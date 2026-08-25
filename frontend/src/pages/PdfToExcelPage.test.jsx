import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PdfToExcelPage from './PdfToExcelPage';

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
    id: 'excel-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset();
  jobState.reset.mockReset();
});

test('submits page-sheet extraction controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'report.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByLabelText('Include text outside tables'));
  await user.click(screen.getByRole('button', {
    name: 'Convert PDF to Excel',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'pdf-to-excel',
    [pdf],
    {
      sheetMode: 'pages',
      includeNonTableText: false,
    },
  );
});

test('explains table-only mode and hides text control', async () => {
  const user = userEvent.setup();
  renderPage();

  await user.selectOptions(
    screen.getByLabelText('Worksheet layout'),
    'tables',
  );

  expect(screen.queryByLabelText('Include text outside tables'))
    .not.toBeInTheDocument();
  expect(screen.getByText(/separate table worksheets/i)).toBeVisible();
});

test('states table fidelity and worker limits', () => {
  renderPage();

  expect(screen.getByText(/table detection is heuristic/i)).toBeVisible();
  expect(screen.getByText(/killable Java worker/i)).toBeVisible();
});

const renderPage = () => render(
  <MemoryRouter>
    <PdfToExcelPage />
  </MemoryRouter>,
);
