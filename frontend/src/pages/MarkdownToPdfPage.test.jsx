import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import MarkdownToPdfPage from './MarkdownToPdfPage';

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

test('submits an uploaded Markdown bundle with print controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const bundle = new File(['PK'], 'document-markdown.zip', {
    type: 'application/zip',
  });
  await user.upload(container.querySelector('input[type="file"]'), bundle);
  await user.selectOptions(screen.getByLabelText('Paper size'), 'legal');
  await user.click(screen.getByRole('button', {
    name: 'Convert Markdown to PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'markdown-to-pdf',
    [bundle],
    {
      pageSize: 'legal',
      orientation: 'portrait',
      printBackground: true,
      marginMm: 10,
    },
  );
});

test('converts pasted Markdown as document.md', async () => {
  const user = userEvent.setup();
  renderPage();
  await user.click(screen.getByRole('button', { name: 'Paste Markdown' }));
  const convert = screen.getByRole('button', {
    name: 'Convert Markdown to PDF',
  });
  expect(convert).toBeDisabled();

  await user.type(screen.getByLabelText('Markdown text'), '# Notes');
  await user.click(convert);

  const [operation, [file]] = jobState.start.mock.calls[0];
  expect(operation).toBe('markdown-to-pdf');
  expect(file.name).toBe('document.md');
  expect(file.type).toBe('text/markdown');
  expect(await file.text()).toBe('# Notes');
  expect(jobState.reset).toHaveBeenCalled();
});

test('blocks pasted Markdown over the size limit', async () => {
  const user = userEvent.setup();
  renderPage();
  await user.click(screen.getByRole('button', { name: 'Paste Markdown' }));

  fireEvent.change(screen.getByLabelText('Markdown text'), {
    target: { value: 'a'.repeat(2 * 1024 * 1024 + 1) },
  });

  expect(screen.getByRole('alert')).toHaveTextContent(/2 MiB or smaller/i);
  expect(screen.getByRole('button', {
    name: 'Convert Markdown to PDF',
  })).toBeDisabled();
});

const renderPage = () => render(
  <MemoryRouter>
    <MarkdownToPdfPage />
  </MemoryRouter>,
);
