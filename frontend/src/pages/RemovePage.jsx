import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ArrowLeft, Download, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Document, Page } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import '../lib/pdfWorker';
import Button from '../components/Button';
import FileUpload from '../components/FileUpload';
import ToastContainer from '../components/Toast';
import { parsePageExpression } from '../features/editor/pageExpression';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './RemovePage.css';

const PREVIEW_PAGE_LIMIT = 100;

const RemovePage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [pagesInput, setPagesInput] = useState('');
  const [toasts, setToasts] = useState([]);
  const urlRef = useRef(null);
  const handledJobRef = useRef(null);
  const {
    job,
    running,
    connectionError,
    start,
    cancel,
    reset,
  } = usePdfJob();

  const addToast = useCallback((message, type = 'success', duration = 5000) => {
    setToasts((current) => [
      ...current,
      { id: Date.now(), message, type, duration },
    ]);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const removalPlan = useMemo(() => {
    if (!numPages || !pagesInput.trim()) {
      return { pages: [], error: null };
    }
    try {
      const pages = parsePageExpression(pagesInput, numPages, {
        duplicatePolicy: 'reject',
      });
      if (pages.length === numPages) {
        return {
          pages,
          error: 'At least one page must remain in the PDF.',
        };
      }
      return { pages, error: null };
    } catch (error) {
      return { pages: [], error: error.message };
    }
  }, [numPages, pagesInput]);

  const selectedPages = useMemo(
    () => new Set(removalPlan.pages),
    [removalPlan.pages],
  );

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Page removal download started!', 'success');
    } catch (error) {
      console.error('Remove Pages download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download the updated PDF'),
        'error',
      );
    }
  }, [addToast]);

  useEffect(() => () => {
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
    }
  }, []);

  useEffect(() => {
    if (!job || !['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status)) {
      return;
    }
    const resultKey = `${job.id}:${job.status}`;
    if (handledJobRef.current === resultKey) {
      return undefined;
    }
    handledJobRef.current = resultKey;
    let active = true;
    const handleResult = async () => {
      await Promise.resolve();
      if (!active) return;
      if (job.status === 'FAILED') {
        addToast(job.errorMessage || 'Failed to remove pages', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('Page removal cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The job completed without a PDF output.', 'error');
        return;
      }
      downloadOutput(output);
    };
    handleResult();
    return () => {
      active = false;
    };
  }, [addToast, downloadOutput, job]);

  const handleFilesChange = useCallback((files) => {
    if (running) return;
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
      urlRef.current = null;
    }
    const nextFile = files[0] || null;
    const nextUrl = nextFile ? URL.createObjectURL(nextFile) : null;
    urlRef.current = nextUrl;
    setFile(nextFile);
    setFileUrl(nextUrl);
    setNumPages(null);
    setPagesInput('');
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handlePageToggle = useCallback((pageNumber) => {
    if (!numPages || running) return;
    const next = new Set(removalPlan.pages);
    if (next.has(pageNumber)) {
      next.delete(pageNumber);
    } else if (next.size < numPages - 1) {
      next.add(pageNumber);
    } else {
      addToast('At least one page must remain in the PDF.', 'error');
      return;
    }
    setPagesInput([...next].sort((left, right) => left - right).join(','));
  }, [
    addToast,
    numPages,
    removalPlan.pages,
    running,
  ]);

  const handleRemove = async () => {
    if (!file) {
      addToast('Upload a PDF before removing pages.', 'error');
      return;
    }
    if (!numPages || !pagesInput.trim()) {
      addToast('Enter at least one page or range to remove.', 'error');
      return;
    }
    if (removalPlan.error) {
      addToast(removalPlan.error, 'error');
      return;
    }
    try {
      await start('remove', [file], { pages: pagesInput.trim() });
    } catch (error) {
      console.error('Remove Pages job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start page removal'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Remove Pages cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const previewPages = Math.min(numPages || 0, PREVIEW_PAGE_LIMIT);
  const canSubmit = Boolean(
    file
      && numPages
      && pagesInput.trim()
      && removalPlan.pages.length
      && !removalPlan.error
      && !running,
  );

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <Trash2 size={28} />
          <h1>Remove Pages</h1>
        </div>
        <p className="operation-description">
          Delete page ranges while preserving the order of every page you keep.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload PDF</h3>
            <FileUpload
              onFilesChange={handleFilesChange}
              files={file ? [file] : []}
              multiple={false}
              disabled={running}
            />
          </div>

          {file && (
            <>
              <div className="sidebar-section">
                <label className="remove-pages-field">
                  Pages to remove
                  <textarea
                    aria-label="Pages to remove"
                    value={pagesInput}
                    onChange={(event) => setPagesInput(event.target.value)}
                    placeholder="2,4-6,9-"
                    rows={4}
                    disabled={running}
                  />
                  <span className={removalPlan.error ? 'is-error' : ''}>
                    {removalPlan.error
                      || `${removalPlan.pages.length} page(s) selected`}
                  </span>
                </label>
                <p className="remove-pages-help">
                  Use pages, ranges, <code>odd</code>, or <code>even</code>.
                  Duplicate and all-page selections are rejected.
                </p>
              </div>

              <div className="sidebar-actions">
                {job && (
                  <JobProgress
                    job={job}
                    connectionError={connectionError}
                    onCancel={handleCancel}
                  />
                )}
                {job?.status === 'COMPLETED' && job.outputs[0] && (
                  <Button
                    onClick={() => downloadOutput(job.outputs[0])}
                    variant="outline"
                    icon={<Download size={20} />}
                    fullWidth
                  >
                    Download result again
                  </Button>
                )}
                <Button
                  onClick={handleRemove}
                  loading={running}
                  disabled={!canSubmit}
                  variant="danger"
                  icon={<Trash2 size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Removing...' : 'Remove & Download'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <h3>
                  Preview: {file.name} {numPages && `(${numPages} pages)`}
                </h3>
                <span className="preview-hint">
                  Select a page card to toggle removal
                </span>
              </div>
              <div className="remove-page-grid">
                <Document
                  file={fileUrl}
                  onLoadSuccess={({ numPages: loadedPages }) => {
                    setNumPages(loadedPages);
                  }}
                  loading={(
                    <div className="loading-placeholder">Loading PDF...</div>
                  )}
                  error={(
                    <div className="loading-placeholder">
                      Failed to load PDF.
                    </div>
                  )}
                >
                  {Array.from(
                    { length: previewPages },
                    (_, index) => index + 1,
                  ).map((pageNumber) => {
                    const selected = selectedPages.has(pageNumber);
                    return (
                      <button
                        className={`remove-page-card ${
                          selected ? 'is-selected' : ''
                        }`}
                        key={pageNumber}
                        type="button"
                        aria-label={`Remove page ${pageNumber}`}
                        aria-pressed={selected}
                        onClick={() => handlePageToggle(pageNumber)}
                        disabled={running}
                      >
                        <Page
                          pageNumber={pageNumber}
                          width={150}
                          renderTextLayer={false}
                          renderAnnotationLayer={false}
                        />
                        <span className="remove-page-card__label">
                          Page {pageNumber}
                          <strong>{selected ? 'Remove' : 'Keep'}</strong>
                        </span>
                      </button>
                    );
                  })}
                </Document>
                {numPages > PREVIEW_PAGE_LIMIT && (
                  <p className="remove-preview-limit">
                    Showing the first {PREVIEW_PAGE_LIMIT} of {numPages} pages.
                  </p>
                )}
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Trash2 size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Choose page cards or enter a precise page expression.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default RemovePage;
