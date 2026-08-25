import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ArrowLeft, Download, Scissors } from 'lucide-react';
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
import './SplitPage.css';

const MAX_OUTPUTS = 500;
const MAX_FIXED_GROUP_SIZE = 500;
const PREVIEW_PAGE_LIMIT = 100;

const SplitPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [mode, setMode] = useState('individual');
  const [rangeText, setRangeText] = useState('');
  const [fixedGroupSize, setFixedGroupSize] = useState(2);
  const [toasts, setToasts] = useState([]);
  const [failedOutput, setFailedOutput] = useState(null);
  const [downloadingOutput, setDownloadingOutput] = useState(false);
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

  const downloadOutput = useCallback(async (output) => {
    setDownloadingOutput(true);
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      setFailedOutput(null);
      addToast('PDF split download started!', 'success');
    } catch (error) {
      console.error('Split download error:', error);
      setFailedOutput(output);
      addToast(getApiErrorMessage(error, 'Failed to download split ZIP'), 'error');
    } finally {
      setDownloadingOutput(false);
    }
  }, [addToast]);

  useEffect(() => () => {
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
    }
  }, []);

  useEffect(() => {
    if (!job || !['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status)) {
      return undefined;
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
        addToast(job.errorMessage || 'Failed to split PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF split cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The split job completed without a ZIP output.', 'error');
        return;
      }
      await downloadOutput(output);
    };
    handleResult();
    return () => {
      active = false;
    };
  }, [addToast, downloadOutput, job]);

  const rangePlan = useMemo(() => {
    if (mode !== 'ranges' || !numPages) {
      return { groups: [], error: null };
    }
    const expressions = rangeText
      .split('\n')
      .map((expression) => expression.trim())
      .filter(Boolean);
    if (expressions.length === 0) {
      return { groups: [], error: 'Enter one page range per line.' };
    }
    if (expressions.length > MAX_OUTPUTS) {
      return { groups: [], error: `Use at most ${MAX_OUTPUTS} ranges.` };
    }

    try {
      const assigned = new Set();
      const groups = expressions.map((expression, index) => {
        const pages = parsePageExpression(expression, numPages, {
          duplicatePolicy: 'reject',
        });
        pages.forEach((page) => {
          if (assigned.has(page)) {
            throw new Error(`Page ${page} appears in more than one range.`);
          }
          assigned.add(page);
        });
        return { position: index + 1, expression, pages };
      });
      return { groups, error: null };
    } catch (error) {
      return {
        groups: [],
        error: error.message || 'The split ranges are invalid.',
      };
    }
  }, [mode, numPages, rangeText]);

  const handleFilesChange = useCallback((files) => {
    if (running) return;
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
      urlRef.current = null;
    }
    reset();
    handledJobRef.current = null;
    setFailedOutput(null);
    setNumPages(null);
    const nextFile = files[0] || null;
    setFile(nextFile);
    if (nextFile) {
      const url = URL.createObjectURL(nextFile);
      urlRef.current = url;
      setFileUrl(url);
    } else {
      setFileUrl(null);
    }
  }, [reset, running]);

  const changeMode = (nextMode) => {
    if (running) return;
    reset();
    handledJobRef.current = null;
    setFailedOutput(null);
    setMode(nextMode);
  };

  const buildOptions = () => {
    if (mode === 'fixed') {
      return { mode, fixedGroupSize: Number(fixedGroupSize) };
    }
    if (mode === 'ranges') {
      return {
        mode,
        ranges: rangeText
          .split('\n')
          .map((expression) => expression.trim())
          .filter(Boolean),
      };
    }
    return { mode };
  };

  const validate = () => {
    if (!file) return 'Upload one PDF to split.';
    if (!numPages) return 'Wait for the PDF preview to finish loading.';
    if (mode === 'individual' && numPages > MAX_OUTPUTS) {
      return `Individual mode supports at most ${MAX_OUTPUTS} pages.`;
    }
    if (mode === 'ranges' && rangePlan.error) return rangePlan.error;
    if (
      mode === 'fixed'
      && (!Number.isInteger(Number(fixedGroupSize))
        || Number(fixedGroupSize) < 1
        || Number(fixedGroupSize) > MAX_FIXED_GROUP_SIZE)
    ) {
      return `Group size must be between 1 and ${MAX_FIXED_GROUP_SIZE}.`;
    }
    return null;
  };

  const handleSplit = async () => {
    const error = validate();
    if (error) {
      addToast(error, 'error');
      return;
    }
    handledJobRef.current = null;
    setFailedOutput(null);
    try {
      await start('split', [file], buildOptions());
    } catch (startError) {
      console.error('Split error:', startError);
      addToast(getApiErrorMessage(startError, 'Failed to split PDF'), 'error');
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Split cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel split'), 'error');
    }
  };

  const groupForPage = (pageNumber) => {
    if (mode === 'individual') return pageNumber;
    if (mode === 'fixed') {
      return Math.floor((pageNumber - 1) / Number(fixedGroupSize || 1)) + 1;
    }
    return rangePlan.groups.find((group) => group.pages.includes(pageNumber))?.position;
  };

  const previewPages = Math.min(numPages || 0, PREVIEW_PAGE_LIMIT);

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <Scissors size={28} />
          <h1>Split PDF</h1>
        </div>
        <p className="operation-description">
          Split into individual pages, explicit ranges, or fixed-size groups.
          Download every result in one ZIP.
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
            />
          </div>

          {file && (
            <>
              <div className="sidebar-section">
                <h3 className="sidebar-title">Split mode</h3>
                <div className="split-mode-toggle split-mode-toggle--three">
                  {[
                    ['individual', 'Pages'],
                    ['ranges', 'Ranges'],
                    ['fixed', 'Fixed'],
                  ].map(([value, label]) => (
                    <button
                      type="button"
                      className={`mode-btn ${mode === value ? 'active' : ''}`}
                      onClick={() => changeMode(value)}
                      disabled={running}
                      key={value}
                    >
                      {label}
                    </button>
                  ))}
                </div>

                {mode === 'individual' && (
                  <p className="mode-description">
                    Create one PDF per page, up to {MAX_OUTPUTS} outputs.
                  </p>
                )}
                {mode === 'ranges' && (
                  <label className="split-ranges-field">
                    One page expression per output
                    <textarea
                      value={rangeText}
                      onChange={(event) => setRangeText(event.target.value)}
                      placeholder={'1-3\n4,6\n7-'}
                      rows={5}
                      disabled={running}
                    />
                    <span className={rangePlan.error ? 'is-error' : ''}>
                      {rangePlan.error
                        || `${rangePlan.groups.length} output document(s)`}
                    </span>
                  </label>
                )}
                {mode === 'fixed' && (
                  <label className="split-fixed-field">
                    Pages per output
                    <input
                      type="number"
                      min="1"
                      max={Math.min(MAX_FIXED_GROUP_SIZE, numPages || MAX_FIXED_GROUP_SIZE)}
                      value={fixedGroupSize}
                      onChange={(event) => setFixedGroupSize(event.target.value)}
                      disabled={running}
                    />
                  </label>
                )}
              </div>

              <div className="sidebar-actions">
                {job && (
                  <JobProgress
                    job={job}
                    connectionError={connectionError}
                    onCancel={handleCancel}
                  />
                )}
                {failedOutput && (
                  <Button
                    onClick={() => downloadOutput(failedOutput)}
                    loading={downloadingOutput}
                    disabled={downloadingOutput}
                    variant="outline"
                    icon={<Download size={20} />}
                    fullWidth
                  >
                    {downloadingOutput ? 'Downloading...' : 'Retry download'}
                  </Button>
                )}
                <Button
                  onClick={handleSplit}
                  loading={running}
                  disabled={running}
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Splitting...' : 'Split & Download ZIP'}
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
                <span className="preview-hint">Output groups are numbered below</span>
              </div>
              <div className="split-page-grid">
                <Document
                  file={fileUrl}
                  onLoadSuccess={({ numPages: loadedPages }) => setNumPages(loadedPages)}
                  loading={<div className="loading-placeholder">Loading PDF...</div>}
                  error={<div className="loading-placeholder">Failed to load PDF.</div>}
                >
                  {Array.from(
                    { length: previewPages },
                    (_, index) => index + 1,
                  ).map((pageNumber) => {
                    const group = groupForPage(pageNumber);
                    return (
                      <div className="split-page-item" key={pageNumber}>
                        <Page
                          pageNumber={pageNumber}
                          width={150}
                          renderTextLayer={false}
                          renderAnnotationLayer={false}
                        />
                        <div className="page-number">
                          Page {pageNumber}
                          {group && (
                            <span className="group-indicator">
                              Output {group}
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </Document>
                {numPages > PREVIEW_PAGE_LIMIT && (
                  <p className="split-preview-limit">
                    Showing the first {PREVIEW_PAGE_LIMIT} of {numPages} pages.
                  </p>
                )}
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Scissors size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Choose how pages should be grouped before downloading the ZIP.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default SplitPage;
