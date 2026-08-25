import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import CropPage from './CropPage';

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
        onLoadSuccess?.({ numPages: 3 });
      }, [onLoadSuccess]);
      return <div>{children}</div>;
    },
    Page: ({ pageNumber }) => <div>Page preview {pageNumber}</div>,
    pdfjs: {
      GlobalWorkerOptions: {},
    },
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
    id: 'crop-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/crop-job/outputs/output-1',
  );
});

test('submits one shared crop from visual margins', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <CropPage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText('Page preview 1');

  await fillMargin(user, 'Top margin (%)', '10');
  await fillMargin(user, 'Right margin (%)', '20');
  await fillMargin(user, 'Bottom margin (%)', '10');
  await fillMargin(user, 'Left margin (%)', '20');
  await user.click(screen.getByRole('button', { name: 'Crop & Download' }));

  expect(jobState.start).toHaveBeenCalledWith('crop', [file], {
    crop: {
      x: 0.2,
      y: 0.1,
      width: 0.6,
      height: 0.8,
    },
  });

});

test('quantizes the exact minimum crop boundary', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <CropPage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText('Page preview 1');

  await fillMargin(user, 'Left margin (%)', '99');
  await fillMargin(user, 'Right margin (%)', '0.9');
  await user.click(screen.getByRole('button', { name: 'Crop & Download' }));

  expect(jobState.start).toHaveBeenCalledWith('crop', [file], {
    crop: {
      x: 0.99,
      y: 0,
      width: 0.001,
      height: 1,
    },
  });
});

test('submits independent per-page crops and rejects invalid margins', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <CropPage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText('Page preview 1');
  await user.click(screen.getByRole('button', { name: 'Per page' }));
  await user.click(screen.getByRole('button', { name: 'Next page' }));
  await fillMargin(user, 'Left margin (%)', '25');
  await user.click(screen.getByRole('button', { name: 'Crop & Download' }));

  expect(jobState.start).toHaveBeenCalledWith('crop', [file], {
    crops: [{
      pages: '2',
      rectangle: {
        x: 0.25,
        y: 0,
        width: 0.75,
        height: 1,
      },
    }],
  });

  await fillMargin(user, 'Right margin (%)', '80');
  expect(
    await screen.findByText('Horizontal margins must leave visible content.'),
  ).toBeVisible();
  expect(
    screen.getByRole('button', { name: 'Crop & Download' }),
  ).toBeDisabled();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <CropPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'crop-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Processing',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <CropPage />
    </MemoryRouter>,
  );
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'crop-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_cropped.pdf',
      downloadUrl: '/api/v1/jobs/crop-job/outputs/output-1',
    }],
  };
  jobState.running = false;
  rerender(
    <MemoryRouter>
      <CropPage />
    </MemoryRouter>,
  );

  await waitFor(() => {
    expect(jobServiceMock.getDownloadUrl).toHaveBeenCalledWith(
      jobState.job.outputs[0],
    );
    expect(click).toHaveBeenCalledOnce();
  });
  click.mockRestore();
});

const fillMargin = async (user, label, value) => {
  const input = screen.getByLabelText(label);
  await user.clear(input);
  await user.type(input, value);
};
