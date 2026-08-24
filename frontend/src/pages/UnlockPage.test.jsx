import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import UnlockPage from './UnlockPage';

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
    id: 'unlock-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/unlock-job/outputs/output-1',
  );
});

test('submits a confirmed Unicode password', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const file = new File(['pdf'], 'locked.pdf', { type: 'application/pdf' });

  await user.upload(container.querySelector('input[type="file"]'), file);
  await fill(user, 'Current password', 'p\u00E4ssword');
  await fill(user, 'Confirm password', 'p\u00E4ssword');
  await user.click(screen.getByRole('button', { name: 'Unlock & Download' }));

  expect(jobState.start).toHaveBeenCalledWith('unlock', [file], {
    password: 'p\u00E4ssword',
  });
});

test('blocks mismatched and overlong passwords', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'locked.pdf', { type: 'application/pdf' }),
  );
  await fill(user, 'Current password', 'open-secret');
  await fill(user, 'Confirm password', 'different');

  expect(await screen.findByText('Passwords do not match.')).toBeVisible();
  expect(
    screen.getByRole('button', { name: 'Unlock & Download' }),
  ).toBeDisabled();

  await fill(user, 'Current password', 'x'.repeat(128));
  await fill(user, 'Confirm password', 'x'.repeat(128));
  expect(
    await screen.findByText('Password must stay within 127 UTF-8 bytes.'),
  ).toBeVisible();
});

test('logs only sanitized submission failure details', async () => {
  const error = Object.assign(new Error('Request failed'), {
    response: {
      status: 400,
      data: { code: 'INVALID_PASSWORD', message: 'Password is incorrect' },
    },
    config: { data: 'password=never-log-this' },
  });
  jobState.start.mockRejectedValue(error);
  const log = vi.spyOn(console, 'error').mockImplementation(() => {});
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'locked.pdf', { type: 'application/pdf' }),
  );
  await fill(user, 'Current password', 'never-log-this');
  await fill(user, 'Confirm password', 'never-log-this');
  await user.click(screen.getByRole('button', { name: 'Unlock & Download' }));

  await waitFor(() => expect(log).toHaveBeenCalledOnce());
  expect(JSON.stringify(log.mock.calls)).not.toContain('never-log-this');
  log.mockRestore();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File(['pdf'], 'locked.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'unlock-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Removing encryption',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'unlock-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'locked_unlocked.pdf',
      downloadUrl: '/api/v1/jobs/unlock-job/outputs/output-1',
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
    <UnlockPage />
  </MemoryRouter>
);

const fill = async (user, label, value) => {
  const input = screen.getByLabelText(label);
  await user.clear(input);
  await user.type(input, value);
};
