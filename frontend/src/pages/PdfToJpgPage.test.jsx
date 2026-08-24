import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PdfToJpgPage from './PdfToJpgPage';

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

vi.mock('react-pdf', () => ({
  Document: ({ onLoadSuccess, children }) => (
    <div>
      <button
        type="button"
        onClick={() => onLoadSuccess({
          numPages: 4,
          getPage: async () => ({
            getViewport: () => ({ width: 612, height: 792 }),
          }),
        })}
      >
        Load PDF
      </button>
      {children}
    </div>
  ),
  Page: ({ pageNumber }) => <div>Preview page {pageNumber}</div>,
  pdfjs: {
    GlobalWorkerOptions: {},
  },
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
    id: 'pdf-jpg-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/pdf-jpg-job/outputs/output-1',
  );
});

test('submits page range resolution and quality controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await user.click(screen.getByRole('button', { name: 'Load PDF' }));
  const pages = await screen.findByLabelText('Pages');
  await user.clear(pages);
  await user.type(pages, '1,3-4');
  await user.selectOptions(screen.getByLabelText('Resolution'), '200');
  await user.clear(screen.getByLabelText('JPEG quality'));
  await user.type(screen.getByLabelText('JPEG quality'), '72');
  await user.click(screen.getByRole(
    'button',
    { name: 'Convert & Download ZIP' },
  ));

  expect(jobState.start).toHaveBeenCalledWith('pdf-to-jpg', [file], {
    pages: '1,3-4',
    dpi: 200,
    quality: 72,
  });
});

test('blocks invalid and duplicate page ranges', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  await user.click(screen.getByRole('button', { name: 'Load PDF' }));
  const pages = await screen.findByLabelText('Pages');
  await user.clear(pages);
  await user.type(pages, '1,1');

  expect(await screen.findByText('Page 1 is selected more than once.'))
    .toBeVisible();
  expect(screen.getByRole(
    'button',
    { name: 'Convert & Download ZIP' },
  )).toBeDisabled();
});

test('supports cancellation and native ZIP download', async () => {
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
    id: 'pdf-jpg-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Rendering pages',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'pdf-jpg-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_jpg.zip',
      downloadUrl: '/api/v1/jobs/pdf-jpg-job/outputs/output-1',
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
    <PdfToJpgPage />
  </MemoryRouter>
);
