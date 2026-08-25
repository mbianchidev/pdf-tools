import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import ProtectPage from './ProtectPage';

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
    id: 'protect-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/protect-job/outputs/output-1',
  );
});

test('submits passwords and explicit permission controls', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <ProtectPage />
    </MemoryRouter>,
  );
  const file = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), file);
  await fill(user, 'Open password', 'open-secret');
  await fill(user, 'Confirm open password', 'open-secret');
  await fill(user, 'Owner password', 'owner-secret');
  await fill(user, 'Confirm owner password', 'owner-secret');
  await user.selectOptions(screen.getByLabelText('Printing'), 'low');
  await user.click(screen.getByLabelText('Allow annotations'));
  await user.click(screen.getByLabelText('Allow form filling'));
  await user.click(screen.getByRole('button', { name: 'Protect & Download' }));

  expect(jobState.start).toHaveBeenCalledWith('protect', [file], {
    userPassword: 'open-secret',
    ownerPassword: 'owner-secret',
    permissions: {
      print: 'low',
      copy: false,
      modify: false,
      annotate: true,
      fillForms: true,
      accessibility: true,
      assemble: false,
    },
  });
});

test('blocks mismatched and identical passwords', async () => {
  const user = userEvent.setup();
  const { container } = render(
    <MemoryRouter>
      <ProtectPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  await fill(user, 'Open password', 'same-secret');
  await fill(user, 'Confirm open password', 'different');
  await fill(user, 'Owner password', 'same-secret');
  await fill(user, 'Confirm owner password', 'same-secret');

  expect(await screen.findByText('Open passwords do not match.')).toBeVisible();
  expect(
    screen.getByRole('button', { name: 'Protect & Download' }),
  ).toBeDisabled();

  await fill(user, 'Confirm open password', 'same-secret');
  expect(
    await screen.findByText('Open and owner passwords must differ.'),
  ).toBeVisible();

  await fill(user, 'Open password', 'ow\u00ADner');
  await fill(user, 'Confirm open password', 'ow\u00ADner');
  await fill(user, 'Owner password', 'owner');
  await fill(user, 'Confirm owner password', 'owner');
  expect(
    await screen.findByText(
      'Passwords must contain printable ASCII characters only.',
    ),
  ).toBeVisible();
  expect(jobState.start).not.toHaveBeenCalled();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const { container, rerender } = render(
    <MemoryRouter>
      <ProtectPage />
    </MemoryRouter>,
  );
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'protect-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Encrypting',
    outputs: [],
  };
  jobState.running = true;
  rerender(
    <MemoryRouter>
      <ProtectPage />
    </MemoryRouter>,
  );
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'protect-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_protected.pdf',
      downloadUrl: '/api/v1/jobs/protect-job/outputs/output-1',
    }],
  };
  jobState.running = false;
  rerender(
    <MemoryRouter>
      <ProtectPage />
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

const fill = async (user, label, value) => {
  const input = screen.getByLabelText(label);
  await user.clear(input);
  await user.type(input, value);
};
