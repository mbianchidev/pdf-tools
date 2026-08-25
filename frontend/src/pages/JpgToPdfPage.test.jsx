import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import JpgToPdfPage from './JpgToPdfPage';

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
    id: 'jpg-pdf-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/jpg-pdf-job/outputs/output-1',
  );
  vi.stubGlobal('URL', {
    createObjectURL: vi.fn((file) => `blob:${file.name}`),
    revokeObjectURL: vi.fn(),
  });
});

test('reorders images and submits paper controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const first = new File(['first'], 'first.jpg', { type: 'image/jpeg' });
  const second = new File(['second'], 'second.jpg', { type: 'image/jpeg' });
  await user.upload(
    container.querySelector('input[type="file"]'),
    [first, second],
  );
  await user.click(screen.getByRole('button', {
    name: 'Move second.jpg earlier',
  }));
  await user.selectOptions(screen.getByLabelText('Page size'), 'letter');
  await user.selectOptions(screen.getByLabelText('Orientation'), 'landscape');
  await user.clear(screen.getByLabelText('Margin'));
  await user.type(screen.getByLabelText('Margin'), '36');
  await user.click(screen.getByRole('button', {
    name: 'Create & Download PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'jpg-to-pdf',
    [second, first],
    {
      pageSize: 'letter',
      orientation: 'landscape',
      margin: 36,
    },
  );
});

test('blocks an out-of-range margin', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['image'], 'photo.jpg', { type: 'image/jpeg' }),
  );
  await user.clear(screen.getByLabelText('Margin'));
  await user.type(screen.getByLabelText('Margin'), '145');

  expect(await screen.findByText('Margin must be between 0 and 144 points.'))
    .toBeVisible();
  expect(screen.getByRole('button', {
    name: 'Create & Download PDF',
  })).toBeDisabled();
});

test('enforces the image limit across multiple upload batches', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const input = container.querySelector('input[type="file"]');
  const firstBatch = Array.from({ length: 60 }, (_, index) => new File(
    ['image'],
    `first-${index}.jpg`,
    { type: 'image/jpeg' },
  ));
  const secondBatch = Array.from({ length: 40 }, (_, index) => new File(
    ['image'],
    `second-${index}.jpg`,
    { type: 'image/jpeg' },
  ));

  await user.upload(input, firstBatch);
  await user.upload(input, secondBatch);

  expect(input).toBeDisabled();
  expect(screen.getAllByRole('button', { name: 'Remove file' })).toHaveLength(
    100,
  );
});

test('supports cancellation and native PDF download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File(['image'], 'photo.jpg', { type: 'image/jpeg' }),
  );
  jobState.job = {
    id: 'jpg-pdf-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Adding images',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'jpg-pdf-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'images.pdf',
      downloadUrl: '/api/v1/jobs/jpg-pdf-job/outputs/output-1',
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
    <JpgToPdfPage />
  </MemoryRouter>
);
