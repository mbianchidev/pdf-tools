import {
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import RedactPage from './RedactPage';

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

vi.mock('react-pdf', async () => {
  const React = await import('react');
  return {
    Document: ({ children, onLoadSuccess }) => {
      React.useEffect(() => {
        onLoadSuccess?.({
          numPages: 2,
          getPage: async () => ({
            getViewport: () => ({ width: 600, height: 800 }),
          }),
        });
      }, [onLoadSuccess]);
      return <div>{children}</div>;
    },
    Page: ({ pageNumber }) => <div>PDF page {pageNumber}</div>,
    pdfjs: { GlobalWorkerOptions: {} },
  };
});

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
    id: 'redact-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/redact-job/outputs/output-1',
  );
});

test('submits normalized redaction areas across pages', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  const canvas = await screen.findByRole('application', {
    name: 'Redaction canvas',
  });
  canvas.getBoundingClientRect = pageRectangle;

  draw(canvas, 60, 80, 300, 320);
  await user.click(screen.getByRole('button', { name: 'Next page' }));
  draw(canvas, 120, 160, 240, 400);
  await user.click(screen.getByRole('button', {
    name: 'Redact securely & Download',
  }));

  expect(jobState.start).toHaveBeenCalledWith('redact', [pdf], {
    areas: [
      { page: 1, x: 0.1, y: 0.1, width: 0.4, height: 0.3 },
      { page: 2, x: 0.2, y: 0.2, width: 0.2, height: 0.3 },
    ],
  });
});

test('ignores tiny drags and deletes selected areas', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  const canvas = await screen.findByRole('application', {
    name: 'Redaction canvas',
  });
  canvas.getBoundingClientRect = pageRectangle;

  draw(canvas, 60, 80, 62, 82);
  expect(screen.getByText('No redaction areas yet')).toBeVisible();
  expect(screen.getByRole('button', {
    name: 'Redact securely & Download',
  })).toBeDisabled();

  draw(canvas, 60, 80, 300, 320);
  expect(screen.getByText('1 area')).toBeVisible();
  await user.click(screen.getByRole('button', {
    name: 'Delete redaction area 1',
  }));
  expect(screen.getByText('No redaction areas yet')).toBeVisible();
});

test('explains irreversible rasterization before submission', async () => {
  const { container } = renderPage();
  await userEvent.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );

  expect(await screen.findByText(
    /rasterized into a new PDF/i,
  )).toBeVisible();
  expect(screen.getByText(
    /searchable text, links, forms, and original objects are removed/i,
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
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'redact-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Rasterizing pages',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'redact-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_redacted.pdf',
      downloadUrl: '/api/v1/jobs/redact-job/outputs/output-1',
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

const draw = (canvas, startX, startY, endX, endY) => {
  fireEvent.pointerDown(canvas, {
    clientX: startX,
    clientY: startY,
    pointerId: 1,
  });
  fireEvent.pointerMove(canvas, {
    clientX: endX,
    clientY: endY,
    pointerId: 1,
  });
  fireEvent.pointerUp(canvas, {
    clientX: endX,
    clientY: endY,
    pointerId: 1,
  });
};

const pageRectangle = () => ({
  left: 0,
  top: 0,
  width: 600,
  height: 800,
});

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <RedactPage />
  </MemoryRouter>
);
