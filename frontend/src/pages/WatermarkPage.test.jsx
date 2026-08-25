import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import WatermarkPage from './WatermarkPage';

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
    <button
      type="button"
      onClick={() => onLoadSuccess({
        numPages: 3,
        getPage: async () => ({
          getViewport: () => ({ width: 600, height: 800 }),
        }),
      })}
    >
      Load PDF
      {children}
    </button>
  ),
  Page: ({ pageNumber }) => <div>Page {pageNumber}</div>,
  pdfjs: { GlobalWorkerOptions: {} },
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
    id: 'watermark-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/watermark-job/outputs/output-1',
  );
  vi.stubGlobal('URL', {
    createObjectURL: vi.fn((file) => `blob:${file.name}`),
    revokeObjectURL: vi.fn(),
  });
});

test('submits styled text watermark controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByRole('button', { name: /Load PDF/ }));
  await user.type(screen.getByLabelText('Watermark text'), 'CONFIDENTIAL');
  await user.clear(screen.getByLabelText('Pages'));
  await user.type(screen.getByLabelText('Pages'), '1,3');
  await user.selectOptions(screen.getByLabelText('Font'), 'times-bold');
  await user.clear(screen.getByLabelText('Font size'));
  await user.type(screen.getByLabelText('Font size'), '48');
  await user.clear(screen.getByLabelText('Opacity'));
  await user.type(screen.getByLabelText('Opacity'), '35');
  await user.click(screen.getByRole('button', {
    name: 'Apply & Download',
  }));

  expect(jobState.start).toHaveBeenCalledWith('watermark', [pdf], {
    mode: 'text',
    pages: '1,3',
    opacity: 0.35,
    rotation: 45,
    x: 0.5,
    y: 0.5,
    text: 'CONFIDENTIAL',
    font: 'times-bold',
    fontSize: 48,
    color: '#4f46e5',
  });
});

test('submits image mode with PDF first', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const inputs = container.querySelectorAll('input[type="file"]');
  const pdf = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(inputs[0], pdf);
  await user.click(screen.getByRole('button', { name: /Load PDF/ }));
  await user.click(screen.getByRole('button', { name: 'Image watermark' }));
  const image = new File(['png'], 'mark.png', { type: 'image/png' });
  await user.upload(
    container.querySelectorAll('input[type="file"]')[1],
    image,
  );
  await user.clear(screen.getByLabelText('Image width'));
  await user.type(screen.getByLabelText('Image width'), '40');
  await user.click(screen.getByRole('button', {
    name: 'Apply & Download',
  }));

  expect(jobState.start).toHaveBeenCalledWith('watermark', [pdf, image], {
    mode: 'image',
    pages: 'all',
    opacity: 0.3,
    rotation: 45,
    x: 0.5,
    y: 0.5,
    imageWidthPercent: 40,
  });
});

test('supports cancellation and native download', async () => {
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
    id: 'watermark-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Applying watermark',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'watermark-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_watermarked.pdf',
      downloadUrl: '/api/v1/jobs/watermark-job/outputs/output-1',
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
    <WatermarkPage />
  </MemoryRouter>
);
