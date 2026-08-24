import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  Images,
} from 'lucide-react';
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
import './PdfToJpgPage.css';

const DPI_OPTIONS = [72, 96, 150, 200, 300];

const PdfToJpgPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(null);
  const [pagesInput, setPagesInput] = useState('all');
  const [dpi, setDpi] = useState(150);
  const [quality, setQuality] = useState(85);
  const [toasts, setToasts] = useState([]);
  const documentRef = useRef(null);
  const pageRequestRef = useRef(0);
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

  const selection = useMemo(() => {
    if (!numPages) {
      return { pages: [], error: null };
    }
    try {
      return {
        pages: parsePageExpression(pagesInput, numPages, {
          duplicatePolicy: 'reject',
        }).sort((left, right) => left - right),
        error: null,
      };
    } catch (error) {
      return { pages: [], error: error.message };
    }
  }, [numPages, pagesInput]);

  const numericError = validateControls(dpi, quality);
  const selected = selection.pages.includes(currentPage);
  const dimensions = pageSize
    ? {
        width: Math.max(Math.floor(pageSize.width * Number(dpi) / 72), 1),
        height: Math.max(Math.floor(pageSize.height * Number(dpi) / 72), 1),
      }
    : null;

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('JPG archive download started!', 'success');
    } catch (error) {
      console.error('PDF to JPG download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download JPG archive'),
        'error',
      );
    }
  }, [addToast]);

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
        addToast(job.errorMessage || 'Failed to convert PDF to JPG', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF to JPG conversion cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The conversion completed without a ZIP output.', 'error');
        return;
      }
      downloadOutput(output);
    };
    handleResult();
    return () => {
      active = false;
    };
  }, [addToast, downloadOutput, job]);

  const loadPageSize = useCallback(async (pageNumber, document) => {
    const request = ++pageRequestRef.current;
    const page = await document.getPage(pageNumber);
    const viewport = page.getViewport({ scale: 1 });
    if (request === pageRequestRef.current) {
      setPageSize({
        width: viewport.width,
        height: viewport.height,
      });
    }
  }, []);

  const handleDocumentLoad = useCallback(async (document) => {
    documentRef.current = document;
    setNumPages(document.numPages);
    setCurrentPage(1);
    await loadPageSize(1, document);
  }, [loadPageSize]);

  const goToPage = async (pageNumber) => {
    if (!documentRef.current || !numPages) return;
    const next = Math.min(Math.max(pageNumber, 1), numPages);
    setCurrentPage(next);
    await loadPageSize(next, documentRef.current);
  };

  const handleFilesChange = useCallback((files) => {
    if (running) return;
    setFile(files[0] || null);
    setNumPages(null);
    setCurrentPage(1);
    setPageSize(null);
    setPagesInput('all');
    setDpi(150);
    setQuality(85);
    handledJobRef.current = null;
    documentRef.current = null;
    reset();
  }, [reset, running]);

  const handleSubmit = async () => {
    const error = selection.error || numericError;
    if (!file || !numPages || selection.pages.length === 0 || error) {
      addToast(error || 'Upload a PDF before converting it.', 'error');
      return;
    }
    try {
      await start('pdf-to-jpg', [file], {
        pages: pagesInput.trim(),
        dpi: Number(dpi),
        quality: Number(quality),
      });
    } catch (startError) {
      console.error('PDF to JPG job failed:', startError);
      addToast(
        getApiErrorMessage(startError, 'Failed to start PDF conversion'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('PDF to JPG cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const canSubmit = Boolean(
    file
    && numPages
    && selection.pages.length > 0
    && !selection.error
    && !numericError
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
          <Images size={28} />
          <h1>PDF to JPG</h1>
        </div>
        <p className="operation-description">
          Render selected pages at a controlled resolution and JPEG quality.
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
              <div className="sidebar-section pdf-jpg-controls">
                <h3 className="sidebar-title">Image settings</h3>
                <label>
                  Pages
                  <input
                    aria-label="Pages"
                    value={pagesInput}
                    onChange={(event) => setPagesInput(event.target.value)}
                    placeholder="all, 1-3, odd"
                    disabled={running}
                  />
                  <span className={selection.error ? 'is-error' : ''}>
                    {selection.error
                      || `${selection.pages.length} page(s) selected`}
                  </span>
                </label>
                <label>
                  Resolution
                  <select
                    aria-label="Resolution"
                    value={dpi}
                    onChange={(event) => setDpi(event.target.value)}
                    disabled={running}
                  >
                    {DPI_OPTIONS.map((value) => (
                      <option value={value} key={value}>
                        {value} DPI
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  JPEG quality
                  <input
                    aria-label="JPEG quality"
                    type="number"
                    min="10"
                    max="100"
                    value={quality}
                    onChange={(event) => setQuality(event.target.value)}
                    disabled={running}
                  />
                  <span className={numericError ? 'is-error' : ''}>
                    {numericError || '10 smaller files · 100 highest quality'}
                  </span>
                </label>
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
                  onClick={handleSubmit}
                  loading={running}
                  disabled={!canSubmit}
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Rendering pages...' : 'Convert & Download ZIP'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <div className="pdf-jpg-preview">
              <Document
                file={file}
                onLoadSuccess={handleDocumentLoad}
                loading={<p>Loading PDF preview...</p>}
              >
                <div className={selected
                  ? 'pdf-jpg-page is-selected'
                  : 'pdf-jpg-page'}>
                  <Page
                    pageNumber={currentPage}
                    width={Math.min(560, window.innerWidth - 48)}
                    renderTextLayer={false}
                    renderAnnotationLayer={false}
                  />
                  <span>{selected ? 'Selected for export' : 'Not selected'}</span>
                </div>
              </Document>
              {numPages && (
                <div className="pdf-jpg-preview-meta">
                  <div className="page-navigation">
                    <button
                      type="button"
                      onClick={() => goToPage(currentPage - 1)}
                      disabled={running || currentPage <= 1}
                      aria-label="Previous page"
                    >
                      <ChevronLeft size={20} />
                    </button>
                    <span>Page {currentPage} of {numPages}</span>
                    <button
                      type="button"
                      onClick={() => goToPage(currentPage + 1)}
                      disabled={running || currentPage >= numPages}
                      aria-label="Next page"
                    >
                      <ChevronRight size={20} />
                    </button>
                  </div>
                  {dimensions && (
                    <p>
                      Output estimate: {dimensions.width} × {dimensions.height}px
                      {' · '}
                      {quality}% quality
                    </p>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="preview-empty">
              <Images size={64} />
              <h3>Upload a PDF to render</h3>
              <p>Select pages, resolution, and JPEG quality.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const validateControls = (dpi, quality) => {
  const numericDpi = Number(dpi);
  if (!Number.isInteger(numericDpi) || numericDpi < 72 || numericDpi > 300) {
    return 'Resolution must be between 72 and 300 DPI.';
  }
  const numericQuality = Number(quality);
  if (
    !Number.isInteger(numericQuality)
    || numericQuality < 10
    || numericQuality > 100
  ) {
    return 'JPEG quality must be between 10 and 100.';
  }
  return null;
};

export default PdfToJpgPage;
