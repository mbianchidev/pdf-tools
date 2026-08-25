import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import ExcelToPdfPage from './ExcelToPdfPage';

const jobState = vi.hoisted(() => ({
  job: null,
  running: false,
  connectionError: false,
  start: vi.fn(),
  cancel: vi.fn(),
  reset: vi.fn(),
}));

vi.mock('../features/jobs/usePdfJob', () => ({
  usePdfJob: () => jobState,
}));

vi.mock('../services/jobService', () => ({
  jobService: { getDownloadUrl: vi.fn() },
  getApiErrorMessage: (error, fallback) => error.message || fallback,
}));

beforeEach(() => {
  jobState.job = null;
  jobState.running = false;
  jobState.connectionError = false;
  jobState.start.mockReset().mockResolvedValue({
    id: 'excel-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset();
  jobState.reset.mockReset();
});

test('submits used cells and landscape orientation', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const workbook = new File(['xlsx'], 'workbook.xlsx', {
    type: 'application/vnd.openxmlformats-officedocument.'
      + 'spreadsheetml.sheet',
  });
  await user.upload(container.querySelector('input[type="file"]'), workbook);
  await user.selectOptions(
    screen.getByLabelText('Print area mode'),
    'used',
  );
  await user.selectOptions(
    screen.getByLabelText('Page orientation'),
    'landscape',
  );
  await user.click(screen.getByRole('button', {
    name: 'Convert Excel to PDF',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'excel-to-pdf',
    [workbook],
    {
      printAreaMode: 'used',
      orientation: 'landscape',
    },
  );
});

test('validates and submits a custom A1 range', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['xlsx'], 'workbook.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.'
        + 'spreadsheetml.sheet',
    }),
  );
  await user.selectOptions(
    screen.getByLabelText('Print area mode'),
    'custom',
  );
  const area = screen.getByLabelText('Custom print area');
  await user.clear(area);
  await user.type(area, 'not-a-range');
  expect(screen.getByRole('button', {
    name: 'Convert Excel to PDF',
  })).toBeDisabled();

  await user.clear(area);
  await user.type(area, 'B2:H30');
  await user.click(screen.getByRole('button', {
    name: 'Convert Excel to PDF',
  }));

  expect(jobState.start.mock.calls[0][2]).toEqual({
    printAreaMode: 'custom',
    printArea: 'B2:H30',
    orientation: 'preserve',
  });
});

const renderPage = () => render(
  <MemoryRouter>
    <ExcelToPdfPage />
  </MemoryRouter>,
);
