import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PdfToMarkdownPage from './PdfToMarkdownPage';

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
    id: 'markdown-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset();
  jobState.reset.mockReset();
});

test('submits Markdown structure controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'report.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByLabelText('Detect lists'));
  await user.click(screen.getByLabelText('Include extracted images'));
  await user.click(screen.getByRole('button', {
    name: 'Convert PDF to Markdown',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'pdf-to-markdown',
    [pdf],
    {
      detectHeadings: true,
      detectLists: false,
      detectTables: true,
      includeImages: false,
      preservePageBreaks: true,
    },
  );
});

test('explains ZIP output and image-only rejection', () => {
  renderPage();

  expect(screen.getByText(/document\.md/i)).toBeVisible();
  expect(screen.getByText(/image-only and scanned PDFs are rejected/i))
    .toBeVisible();
  expect(screen.getByText(/killable Java worker/i)).toBeVisible();
});

const renderPage = () => render(
  <MemoryRouter>
    <PdfToMarkdownPage />
  </MemoryRouter>,
);
