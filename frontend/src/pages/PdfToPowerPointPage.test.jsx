import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import PdfToPowerPointPage from './PdfToPowerPointPage';

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
    id: 'powerpoint-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset();
  jobState.reset.mockReset();
});

test('submits editable slide controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'slides.pdf', {
    type: 'application/pdf',
  });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.selectOptions(
    screen.getByLabelText('Slide size'),
    'widescreen',
  );
  await user.click(screen.getByLabelText('Detect aligned tables'));
  await user.click(screen.getByRole('button', {
    name: 'Convert PDF to PowerPoint',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'pdf-to-powerpoint',
    [pdf],
    {
      mode: 'editable',
      slideSize: 'widescreen',
      includeImages: true,
      detectTables: false,
    },
  );
});

test('explains visual mode and hides editable controls', async () => {
  const user = userEvent.setup();
  renderPage();

  await user.selectOptions(
    screen.getByLabelText('Conversion mode'),
    'visual',
  );

  expect(screen.queryByLabelText('Include raster images'))
    .not.toBeInTheDocument();
  expect(screen.queryByLabelText('Detect aligned tables'))
    .not.toBeInTheDocument();
  expect(screen.getByText(/slide contents are not editable/i)).toBeVisible();
});

test('states editable fidelity and worker limits', () => {
  renderPage();

  expect(screen.getByText(/vector art, clipping, equations/i)).toBeVisible();
  expect(screen.getByText(/killable Java worker/i)).toBeVisible();
});

const renderPage = () => render(
  <MemoryRouter>
    <PdfToPowerPointPage />
  </MemoryRouter>,
);
