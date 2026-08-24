import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import RemovePage from './RemovePage';

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
    id: 'remove-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/remove-job/outputs/output-1',
  );
});

test('submits validated page ranges and toggles page thumbnails', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <RemovePage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText('Page preview 1');

  const pages = screen.getByLabelText('Pages to remove');
  await user.type(pages, '2,4-5');
  await user.click(screen.getByRole('button', { name: 'Remove & Download' }));
  expect(jobState.start).toHaveBeenCalledWith('remove', [file], {
    pages: '2,4-5',
  });

  await user.click(screen.getByRole('button', { name: 'Remove page 3' }));
  expect(pages).toHaveValue('2,3,4,5');
});

test('rejects duplicate and all-page removal before creating a job', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <RemovePage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  await screen.findByText('Page preview 1');
  const pages = screen.getByLabelText('Pages to remove');

  await user.type(pages, '2,2');
  expect(
    await screen.findByText('Page 2 is selected more than once.'),
  ).toBeVisible();
  expect(
    screen.getByRole('button', { name: 'Remove & Download' }),
  ).toBeDisabled();

  await user.clear(pages);
  await user.type(pages, 'all');
  expect(
    await screen.findByText('At least one page must remain in the PDF.'),
  ).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Remove page 3' }));
  expect(pages).toHaveValue('1,2,4,5,6');
  expect(jobState.start).not.toHaveBeenCalled();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <RemovePage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'remove-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Processing',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <RemovePage />
    </MemoryRouter>,
  );
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'remove-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_pages_removed.pdf',
      downloadUrl: '/api/v1/jobs/remove-job/outputs/output-1',
    }],
  };
  jobState.running = false;
  rerender(
    <MemoryRouter>
      <RemovePage />
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
