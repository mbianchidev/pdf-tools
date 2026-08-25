import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import SplitPage from './SplitPage';

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
        onLoadSuccess?.({ numPages: 6 });
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
    id: 'split-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/split-job/outputs/output-1',
  );
});

test('submits individual, fixed, and range split options', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <SplitPage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText('Page preview 1');

  await user.click(screen.getByRole('button', { name: 'Split & Download ZIP' }));
  expect(jobState.start).toHaveBeenLastCalledWith('split', [file], {
    mode: 'individual',
  });

  await user.click(screen.getByRole('button', { name: 'Fixed' }));
  const groupSize = screen.getByLabelText('Pages per output');
  await user.clear(groupSize);
  await user.type(groupSize, '3');
  await user.click(screen.getByRole('button', { name: 'Split & Download ZIP' }));
  expect(jobState.start).toHaveBeenLastCalledWith('split', [file], {
    mode: 'fixed',
    fixedGroupSize: 3,
  });

  await user.click(screen.getByRole('button', { name: 'Ranges' }));
  const ranges = screen.getByLabelText(/One page expression per output/);
  await user.type(ranges, '1-2{enter}4-6');
  await user.click(screen.getByRole('button', { name: 'Split & Download ZIP' }));
  expect(jobState.start).toHaveBeenLastCalledWith('split', [file], {
    mode: 'ranges',
    ranges: ['1-2', '4-6'],
  });
});

test('blocks overlapping ranges before creating a job', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <SplitPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  await screen.findByText('Page preview 1');
  jobState.start.mockClear();

  await user.click(screen.getByRole('button', { name: 'Ranges' }));
  await user.type(
    screen.getByLabelText(/One page expression per output/),
    '1-3{enter}3-5',
  );
  await user.click(screen.getByRole('button', { name: 'Split & Download ZIP' }));

  expect(
    await screen.findAllByText('Page 3 appears in more than one range.'),
  ).not.toHaveLength(0);
  expect(jobState.start).not.toHaveBeenCalled();
});

test('exposes cancellation while splitting', async () => {
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <SplitPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'split-job',
    status: 'RUNNING',
    version: 1,
    progress: 40,
    message: 'Processing',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <SplitPage />
    </MemoryRouter>,
  );

  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();
});

test('starts completed ZIP downloads without buffering a blob', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const { rerender } = render(
    <MemoryRouter>
      <SplitPage />
    </MemoryRouter>,
  );
  jobState.job = {
    id: 'split-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_split.zip',
      downloadUrl: '/api/v1/jobs/split-job/outputs/output-1',
    }],
  };
  rerender(
    <MemoryRouter>
      <SplitPage />
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
