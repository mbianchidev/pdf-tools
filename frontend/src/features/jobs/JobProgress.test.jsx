import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import JobProgress from './JobProgress';

test('shows progress and lets the user cancel a running job', async () => {
  const user = userEvent.setup();
  const onCancel = vi.fn();

  render(
    <JobProgress
      job={{ status: 'RUNNING', progress: 42, message: 'Merging' }}
      onCancel={onCancel}
    />,
  );

  expect(screen.getByRole('progressbar', { name: 'PDF job progress' })).toHaveValue(42);
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(onCancel).toHaveBeenCalledOnce();
});

test('announces structured job failures', () => {
  render(
    <JobProgress
      job={{
        status: 'FAILED',
        progress: 20,
        message: 'Failed',
        errorMessage: 'The input is not a PDF.',
      }}
    />,
  );

  expect(screen.getByText('The input is not a PDF.')).toBeVisible();
  expect(screen.queryByRole('button', { name: 'Cancel PDF job' })).not.toBeInTheDocument();
});
