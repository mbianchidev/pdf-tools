import {
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import WordToPdfPage from './WordToPdfPage';

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
    id: 'word-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/word-job/outputs/output-1',
  );
});

test('submits one DOCX to the Word conversion job', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const word = new File(['docx'], 'report.docx', {
    type: 'application/vnd.openxmlformats-officedocument.'
      + 'wordprocessingml.document',
  });
  await user.upload(container.querySelector('input[type="file"]'), word);
  await user.click(screen.getByRole('button', {
    name: 'Convert Word to PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'word-to-pdf',
    [word],
    {},
  );
});

test('shows converter isolation and fidelity expectations', () => {
  renderPage();

  expect(screen.getByText(/runs without network access/i)).toBeVisible();
  expect(screen.getByText(
    /fonts unavailable on the server may be substituted/i,
  )).toBeVisible();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File(['docx'], 'report.docx', {
      type: 'application/vnd.openxmlformats-officedocument.'
        + 'wordprocessingml.document',
    }),
  );
  jobState.job = {
    id: 'word-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Converting with LibreOffice',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'word-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'report.pdf',
      downloadUrl: '/api/v1/jobs/word-job/outputs/output-1',
    }],
  };
  jobState.running = false;
  rendered.rerender(page());

  await waitFor(() => {
    expect(jobServiceMock.getDownloadUrl).toHaveBeenCalledWith(
      jobState.job.outputs[0],
    );
    expect(click).toHaveBeenCalledOnce();
  });
  click.mockRestore();
});

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <WordToPdfPage />
  </MemoryRouter>
);
