import {
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import EditPage from './EditPage';

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

vi.mock('react-pdf', () => ({
  Document: ({ onLoadSuccess, children }) => (
    <div>
      <button
        type="button"
        onClick={() => onLoadSuccess({
          numPages: 2,
          getPage: async () => ({
            getViewport: () => ({ width: 600, height: 800 }),
          }),
        })}
      >
        Load PDF
      </button>
      {children}
    </div>
  ),
  Page: ({ pageNumber }) => <div>PDF page {pageNumber}</div>,
  pdfjs: { GlobalWorkerOptions: {} },
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
    id: 'edit-job',
    status: 'PENDING',
    version: 0,
  });
  jobState.cancel.mockReset().mockResolvedValue(undefined);
  jobState.reset.mockReset();
  jobServiceMock.getDownloadUrl.mockReset().mockReturnValue(
    'http://localhost/api/v1/jobs/edit-job/outputs/output-1',
  );
  vi.stubGlobal('URL', {
    createObjectURL: vi.fn((file) => `blob:${file.name}`),
    revokeObjectURL: vi.fn(),
  });
});

test('builds one plan with text shapes and annotations', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByRole('button', { name: 'Load PDF' }));
  const canvas = container.querySelector('.edit-preview__page');
  canvas.getBoundingClientRect = () => ({
    left: 0,
    top: 0,
    width: 600,
    height: 800,
  });

  await place(user, canvas, 'Add text', 300, 160);
  await user.clear(screen.getByLabelText('Text'));
  await user.type(screen.getByLabelText('Text'), 'Reviewed');
  await place(user, canvas, 'Add rectangle', 120, 320);
  await place(user, canvas, 'Add highlight', 300, 480);
  await place(user, canvas, 'Add note', 500, 120);
  await user.clear(screen.getByLabelText('Contents'));
  await user.type(screen.getByLabelText('Contents'), 'Check this');
  await user.click(screen.getByRole('button', {
    name: 'Apply edits & Download',
  }));

  expect(jobState.start).toHaveBeenCalledOnce();
  const [operation, files, options] = jobState.start.mock.calls[0];
  expect(operation).toBe('edit');
  expect(files).toEqual([pdf]);
  expect(options.elements.map((element) => element.type)).toEqual([
    'text',
    'rectangle',
    'highlight',
    'note',
  ]);
  expect(options.elements[0]).toEqual(expect.objectContaining({
    page: 1,
    text: 'Reviewed',
  }));
  expect(options.elements[3]).toEqual(expect.objectContaining({
    contents: 'Check this',
  }));
});

test('uploads and references image assets after the PDF', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByRole('button', { name: 'Load PDF' }));
  const image = new File(['png'], 'stamp.png', { type: 'image/png' });
  await user.upload(
    container.querySelectorAll('input[type="file"]')[1],
    image,
  );
  const canvas = container.querySelector('.edit-preview__page');
  canvas.getBoundingClientRect = () => ({
    left: 0,
    top: 0,
    width: 600,
    height: 800,
  });
  await place(user, canvas, 'Add image', 300, 400);
  await user.click(screen.getByRole('button', {
    name: 'Apply edits & Download',
  }));

  expect(jobState.start).toHaveBeenCalledWith(
    'edit',
    [pdf, image],
    {
      elements: [
        expect.objectContaining({
          type: 'image',
          imageIndex: 0,
        }),
      ],
    },
  );
});

test('drops removed image placements and skips unreferenced uploads', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  const pdf = new File(['pdf'], 'source.pdf', { type: 'application/pdf' });
  await user.upload(container.querySelector('input[type="file"]'), pdf);
  await user.click(screen.getByRole('button', { name: 'Load PDF' }));
  const image = new File(['png'], 'unused.png', { type: 'image/png' });
  await user.upload(
    container.querySelectorAll('input[type="file"]')[1],
    image,
  );
  const canvas = container.querySelector('.edit-preview__page');
  canvas.getBoundingClientRect = () => ({
    left: 0,
    top: 0,
    width: 600,
    height: 800,
  });
  await place(user, canvas, 'Add text', 300, 200);
  await user.click(screen.getByRole('button', {
    name: 'Apply edits & Download',
  }));

  expect(jobState.start.mock.calls[0][1]).toEqual([pdf]);

  jobState.start.mockClear();
  await place(user, canvas, 'Add image', 300, 400);
  const removeButtons = screen.getAllByRole('button', { name: 'Remove file' });
  await user.click(removeButtons[1]);
  expect(screen.getByText(/1 element\(s\)/)).toBeVisible();
});

test('blocks API-invalid text before submission', async () => {
  const user = userEvent.setup();
  const { container } = renderPage();
  await user.upload(
    container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  await user.click(screen.getByRole('button', { name: 'Load PDF' }));
  const canvas = container.querySelector('.edit-preview__page');
  canvas.getBoundingClientRect = () => ({
    left: 0,
    top: 0,
    width: 600,
    height: 800,
  });
  await place(user, canvas, 'Add text', 300, 200);
  await user.clear(screen.getByLabelText('Text'));
  await user.type(screen.getByLabelText('Text'), 'caf\u00E9');

  expect(screen.getByText(
    'Text must use printable ASCII within 200 characters.',
  )).toBeVisible();
  expect(screen.getByRole('button', {
    name: 'Apply edits & Download',
  })).toBeDisabled();
});

test('supports cancellation and native result download', async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  const user = userEvent.setup();
  const rendered = renderPage();
  await user.upload(
    rendered.container.querySelector('input[type="file"]'),
    new File(['pdf'], 'source.pdf', { type: 'application/pdf' }),
  );
  jobState.job = {
    id: 'edit-job',
    status: 'RUNNING',
    version: 1,
    progress: 50,
    message: 'Applying edits',
    outputs: [],
  };
  jobState.running = true;
  rendered.rerender(page());
  await user.click(screen.getByRole('button', { name: 'Cancel PDF job' }));
  expect(jobState.cancel).toHaveBeenCalledOnce();

  jobState.job = {
    id: 'edit-job',
    status: 'COMPLETED',
    version: 2,
    progress: 100,
    message: 'Completed',
    outputs: [{
      id: 'output-1',
      filename: 'source_edited.pdf',
      downloadUrl: '/api/v1/jobs/edit-job/outputs/output-1',
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

const place = async (user, canvas, buttonName, x, y) => {
  await user.click(screen.getByRole('button', { name: buttonName }));
  fireEvent.pointerDown(canvas, { clientX: x, clientY: y });
};

const renderPage = () => render(page());

const page = () => (
  <MemoryRouter>
    <EditPage />
  </MemoryRouter>
);
