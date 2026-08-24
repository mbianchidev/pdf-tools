import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import OrganizePage from './OrganizePage';

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
          numPages: 3,
          getPage: async () => ({ rotate: 0 }),
        });
      }, [onLoadSuccess]);
      return <div>{children}</div>;
    },
    Page: ({ pageNumber, rotate }) => (
      <div>Page preview {pageNumber} rotation {rotate}</div>
    ),
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
    id: 'organize-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/organize-job/outputs/output-1',
  );
});

test('reorders, rotates, duplicates, deletes, and submits pages', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <OrganizePage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText(/Page preview 1/);

  await user.click(screen.getByRole('button', { name: 'Duplicate page 1' }));
  await user.click(screen.getByRole('button', { name: 'Rotate page 2' }));
  await user.click(screen.getByRole('button', { name: 'Move page 4 left' }));
  await user.click(screen.getByRole('button', { name: 'Delete page 4' }));
  await user.click(screen.getByRole('button', { name: 'Organize & Download' }));

  expect(jobState.start).toHaveBeenCalledWith('organize', [file], {
    pages: [
      { page: 1, rotation: 0 },
      { page: 1, rotation: 90 },
      { page: 3, rotation: 0 },
    ],
  });
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <OrganizePage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'organize-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Processing',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <OrganizePage />
    </MemoryRouter>,
  );
  expect(
    screen.getByRole('button', { name: 'Rotate page 1' }),
  ).toBeDisabled();
  expect(
    screen.getByRole('button', { name: 'Duplicate page 1' }),
  ).toBeDisabled();
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'organize-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_organized.pdf',
      downloadUrl: '/api/v1/jobs/organize-job/outputs/output-1',
    }],
  };
  jobState.running = false;
  rerender(
    <MemoryRouter>
      <OrganizePage />
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
