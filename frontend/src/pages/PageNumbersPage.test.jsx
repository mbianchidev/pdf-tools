import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PageNumbersPage from './PageNumbersPage';

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
          getPage: async () => ({
            getViewport: () => ({ width: 200, height: 100 }),
          }),
        });
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
    id: 'numbers-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/numbers-job/outputs/output-1',
  );
});

test('submits ranges, starts, templates, fonts, and positions', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <PageNumbersPage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await screen.findByText('Page preview 1');

  await replace(user, 'Pages to number', '2-3');
  await replace(user, 'Numbering starts at', '5');
  await user.selectOptions(
    screen.getByLabelText('Template'),
    'Page {page} of {total}',
  );
  await user.selectOptions(
    screen.getByLabelText('Font'),
    'courier-bold',
  );
  await replace(user, 'Font size', '14');
  await user.selectOptions(
    screen.getByLabelText('Position'),
    'top-right',
  );
  await replace(user, 'Margin', '20');
  await user.click(screen.getByRole('button', { name: 'Next page' }));
  expect(await screen.findByText('Page 5 of 3')).toBeVisible();
  await user.click(
    screen.getByRole('button', { name: 'Add Numbers & Download' }),
  );

  expect(jobState.start).toHaveBeenCalledWith('page-numbers', [file], {
    pages: '2-3',
    start: 5,
    template: 'Page {page} of {total}',
    font: 'courier-bold',
    fontSize: 14,
    position: 'top-right',
    margin: 20,
  });
});

test('rejects duplicate page ranges before submission', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <PageNumbersPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  await screen.findByText('Page preview 1');
  await replace(user, 'Pages to number', '1,1');

  expect(
    await screen.findByText('Page 1 is selected more than once.'),
  ).toBeVisible();
  expect(
    screen.getByRole('button', { name: 'Add Numbers & Download' }),
  ).toBeDisabled();
  expect(jobState.start).not.toHaveBeenCalled();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <PageNumbersPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'numbers-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Processing',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <PageNumbersPage />
    </MemoryRouter>,
  );
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'numbers-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_numbered.pdf',
      downloadUrl: '/api/v1/jobs/numbers-job/outputs/output-1',
    }],
  };
  jobState.running = false;
  rerender(
    <MemoryRouter>
      <PageNumbersPage />
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

const replace = async (user, label, value) => {
  const input = screen.getByLabelText(label);
  await user.clear(input);
  await user.type(input, value);
};
