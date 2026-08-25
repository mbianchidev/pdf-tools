import {
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import ComparePage from './ComparePage';

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
  download: vi.fn(),
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
    id: 'compare-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockImplementation(
    (output) => `http://localhost${output.downloadUrl}`,
  );
  jobServiceMock.download.mockReset().mockResolvedValue(new Blob([
    JSON.stringify(report()),
  ], { type: 'application/json' }));
});

test('submits baseline then candidate with comparison controls', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const inputs = container.querySelectorAll('input[type="file"]');
  const baseline = new File(['base'], 'baseline.pdf', {
    type: 'application/pdf',
  });
  const candidate = new File(['next'], 'candidate.pdf', {
    type: 'application/pdf',
  });
  await user.upload(inputs[0], baseline);
  await user.upload(inputs[1], candidate);
  await user.selectOptions(
    screen.getByLabelText('Render resolution'),
    '144',
  );
  await user.clear(screen.getByLabelText('Pixel tolerance'));
  await user.type(screen.getByLabelText('Pixel tolerance'), '4');
  await user.click(screen.getByRole('button', {
    name: 'Compare PDFs',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'compare',
    [baseline, candidate],
    {
      renderDpi: 144,
      pixelTolerance: 4,
      layoutTolerancePoints: 2,
    },
  );
});

test('shows combined text layout and visual results', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  const inputs = rendered.container.querySelectorAll(
    'input[type="file"]',
  );
  await user.upload(inputs[0], new File(['base'], 'baseline.pdf', {
    type: 'application/pdf',
  }));
  await user.upload(inputs[1], new File(['next'], 'candidate.pdf', {
    type: 'application/pdf',
  }));
  jobState.job = completedJob();
  rendered.rerender(page());

  const result = await screen.findByTestId('comparison-result');
  expect(within(result).getByText('Documents differ')).toBeVisible();
  expect(within(result).getByText('text page')).toBeVisible();
  expect(within(result).getByText('layout page')).toBeVisible();
  expect(within(result).getByText('visual page')).toBeVisible();
  expect(within(result).getByText('Revenue 100')).toBeVisible();
  expect(within(result).getByText('Revenue 120')).toBeVisible();
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  click.mockRestore();
});

test('explains pixel tolerance and downloadable diff images', () => {
  renderPage();

  expect(screen.getByText(/text edits, moved layout, and rendered pixels/i))
    .toBeVisible();
  expect(screen.getByText(/diff PNGs are packaged in the ZIP/i))
    .toBeVisible();
  expect(screen.getByText(/pixel tolerance ignores small channel noise/i))
    .toBeVisible();
});

const completedJob = () => ({
  id: 'compare-job',
  status: 'COMPLETED',
  version: 2,
  progress: 100,
  message: 'Completed',
  outputs: [{
    id: 'archive-output',
    filename: 'baseline-vs-candidate-comparison.zip',
    mediaType: 'application/zip',
    downloadUrl: '/api/v1/jobs/compare-job/outputs/archive-output',
  }, {
    id: 'report-output',
    filename: 'baseline-vs-candidate-comparison-report.json',
    mediaType: 'application/json',
    downloadUrl: '/api/v1/jobs/compare-job/outputs/report-output',
  }],
});

const report = () => ({
  status: 'different',
  summary: {
    baselinePages: 2,
    candidatePages: 2,
    comparedPages: 2,
    textChangedPages: 1,
    layoutChangedPages: 1,
    visualChangedPages: 1,
    totalAddedLines: 1,
    totalRemovedLines: 1,
    maxVisualDifferencePercent: 4.25,
  },
  pages: [{
    page: 1,
    baselinePresent: true,
    candidatePresent: true,
    text: {
      changed: true,
      addedLines: 1,
      removedLines: 1,
      changes: [{
        type: 'removed',
        baselineLine: 2,
        candidateLine: null,
        text: 'Revenue 100',
      }, {
        type: 'added',
        baselineLine: null,
        candidateLine: 2,
        text: 'Revenue 120',
      }],
    },
    layout: {
      changed: true,
      pageGeometryChanged: false,
      movedTextLines: 1,
    },
    visual: {
      changed: true,
      differencePercent: 4.25,
      diffImage: 'visual/page-001-diff.png',
    },
  }, {
    page: 2,
    baselinePresent: true,
    candidatePresent: true,
    text: { changed: false, changes: [] },
    layout: { changed: false },
    visual: { changed: false, differencePercent: 0, diffImage: null },
  }],
});

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <ComparePage />
  </MemoryRouter>
);
