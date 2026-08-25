import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import HtmlToPdfPage from './HtmlToPdfPage';

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
    id: 'html-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset();
  jobState.reset.mockReset();
});

test('submits HTML with explicit print controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const html = new File(
    ['<!doctype html><h1>Report</h1>'],
    'report.html',
    { type: 'text/html' },
  );
  await user.upload(container.querySelector('input[type="file"]'), html);
  await user.selectOptions(screen.getByLabelText('Paper size'), 'letter');
  await user.selectOptions(
    screen.getByLabelText('Page orientation'),
    'landscape',
  );
  await user.clear(screen.getByLabelText('Page margin'));
  await user.type(screen.getByLabelText('Page margin'), '18');
  await user.click(screen.getByLabelText('Print CSS backgrounds'));
  await user.click(screen.getByRole('button', {
    name: 'Convert HTML to PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'html-to-pdf',
    [html],
    {
      pageSize: 'letter',
      orientation: 'landscape',
      printBackground: false,
      marginMm: 18,
    },
  );
});

test('blocks invalid margins with an announced error', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['<p>Report</p>'], 'report.html', { type: 'text/html' }),
  );

  await user.clear(screen.getByLabelText('Page margin'));
  await user.type(screen.getByLabelText('Page margin'), '51');

  expect(screen.getByRole('alert')).toHaveTextContent(/0 to 50 mm/i);
  expect(screen.getByRole('button', {
    name: 'Convert HTML to PDF',
  })).toBeDisabled();
});

test('explains the browser and asset isolation boundary', () => {
  renderPage();

  expect(screen.getByText(/networkless, read-only sidecar/i)).toBeVisible();
  expect(screen.getByText(/external URLs, local files/i)).toBeVisible();
});

const renderPage = () => render(
  <MemoryRouter>
    <HtmlToPdfPage />
  </MemoryRouter>,
);
