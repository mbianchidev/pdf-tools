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
  ListOrdered,
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
import './PageNumbersPage.css';

const TEMPLATES = [
  '{page}',
  'Page {page}',
  '{page} / {total}',
  'Page {page} of {total}',
];

const FONTS = [
  ['helvetica', 'Helvetica'],
  ['helvetica-bold', 'Helvetica Bold'],
  ['times', 'Times'],
  ['times-bold', 'Times Bold'],
  ['courier', 'Courier'],
  ['courier-bold', 'Courier Bold'],
];

const POSITIONS = [
  ['top-left', 'Top left'],
  ['top-center', 'Top center'],
  ['top-right', 'Top right'],
  ['bottom-left', 'Bottom left'],
  ['bottom-center', 'Bottom center'],
  ['bottom-right', 'Bottom right'],
];

const PageNumbersPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(null);
  const [pagesInput, setPagesInput] = useState('all');
  const [startNumber, setStartNumber] = useState(1);
  const [template, setTemplate] = useState('Page {page} of {total}');
  const [font, setFont] = useState('helvetica');
  const [fontSize, setFontSize] = useState(12);
  const [position, setPosition] = useState('bottom-center');
  const [margin, setMargin] = useState(24);
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

  const currentNumber = useMemo(() => {
    const index = selection.pages.indexOf(currentPage);
    return index < 0 ? null : Number(startNumber) + index;
  }, [currentPage, selection.pages, startNumber]);

  const previewText = currentNumber == null || !numPages
    ? null
    : formatTemplate(
        template,
        currentNumber,
        numPages,
        currentPage,
      );

  const numericError = validateNumericInputs(startNumber, fontSize, margin);
  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Numbered PDF download started!', 'success');
    } catch (error) {
      console.error('Page Numbers download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download numbered PDF'),
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
        addToast(job.errorMessage || 'Failed to add page numbers', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('Page numbering cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The numbering job completed without an output.', 'error');
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
    if (!documentRef.current) return;
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
    handledJobRef.current = null;
    documentRef.current = null;
    reset();
  }, [reset, running]);

  const handleSubmit = async () => {
    const error = selection.error || numericError;
    if (!file || !numPages || selection.pages.length === 0 || error) {
      addToast(error || 'Upload a PDF before adding numbers.', 'error');
      return;
    }
    try {
      await start('page-numbers', [file], {
        pages: pagesInput.trim(),
        start: Number(startNumber),
        template,
        font,
        fontSize: Number(fontSize),
        position,
        margin: Number(margin),
      });
    } catch (error) {
      console.error('Page Numbers job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start page numbering'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Page Numbers cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const previewWidth = Math.min(
    650,
    Math.max(280, globalThis.innerWidth - 48),
  );
  const overlayStyle = pageSize
    ? pageNumberStyle(
        position,
        Number(margin) * (previewWidth / pageSize.width),
        Number(fontSize) * (previewWidth / pageSize.width),
        font,
      )
    : {};
  const canSubmit = Boolean(
    file
      && numPages
      && selection.pages.length
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
          <ListOrdered size={28} />
          <h1>Add Page Numbers</h1>
        </div>
        <p className="operation-description">
          Number selected ranges with controlled starts, templates, and positions.
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

          {file && numPages && (
            <>
              <div className="sidebar-section page-number-fields">
                <label>
                  Pages to number
                  <input
                    value={pagesInput}
                    onChange={(event) => setPagesInput(event.target.value)}
                    disabled={running}
                  />
                </label>
                <label>
                  Numbering starts at
                  <input
                    type="number"
                    min="0"
                    max="1000000"
                    value={startNumber}
                    onChange={(event) => setStartNumber(event.target.value)}
                    disabled={running}
                  />
                </label>
                <label>
                  Template
                  <select
                    value={template}
                    onChange={(event) => setTemplate(event.target.value)}
                    disabled={running}
                  >
                    {TEMPLATES.map((value) => (
                      <option key={value} value={value}>{value}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Font
                  <select
                    value={font}
                    onChange={(event) => setFont(event.target.value)}
                    disabled={running}
                  >
                    {FONTS.map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Font size
                  <input
                    type="number"
                    min="6"
                    max="72"
                    value={fontSize}
                    onChange={(event) => setFontSize(event.target.value)}
                    disabled={running}
                  />
                </label>
                <label>
                  Position
                  <select
                    value={position}
                    onChange={(event) => setPosition(event.target.value)}
                    disabled={running}
                  >
                    {POSITIONS.map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Margin
                  <input
                    type="number"
                    min="0"
                    max="144"
                    value={margin}
                    onChange={(event) => setMargin(event.target.value)}
                    disabled={running}
                  />
                </label>
                <p className={
                  selection.error || numericError
                    ? 'page-number-error'
                    : 'page-number-help'
                }>
                  {selection.error
                    || numericError
                    || `${selection.pages.length} page(s) will be numbered.`}
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
                  onClick={handleSubmit}
                  loading={running}
                  disabled={!canSubmit}
                  icon={<ListOrdered size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Adding numbers...' : 'Add Numbers & Download'}
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
                  Preview: {file.name}
                  {numPages && ` (page ${currentPage} of ${numPages})`}
                </h3>
                <div className="page-number-navigation">
                  <button
                    type="button"
                    aria-label="Previous page"
                    onClick={() => goToPage(currentPage - 1)}
                    disabled={running || currentPage === 1}
                  >
                    <ChevronLeft size={17} />
                  </button>
                  <button
                    type="button"
                    aria-label="Next page"
                    onClick={() => goToPage(currentPage + 1)}
                    disabled={running || currentPage === numPages}
                  >
                    <ChevronRight size={17} />
                  </button>
                </div>
              </div>
              <div className="page-number-preview">
                <Document
                  file={file}
                  onLoadSuccess={handleDocumentLoad}
                  loading={(
                    <div className="loading-placeholder">Loading PDF...</div>
                  )}
                  error={(
                    <div className="loading-placeholder">
                      Failed to load PDF.
                    </div>
                  )}
                >
                  <div className="page-number-preview__page">
                    <Page
                      pageNumber={currentPage}
                      width={previewWidth}
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                    />
                    {previewText && (
                      <span
                        className="page-number-preview__text"
                        style={overlayStyle}
                      >
                        {previewText}
                      </span>
                    )}
                  </div>
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <ListOrdered size={64} />
              <h3>Upload a PDF to add page numbers</h3>
              <p>Preview the exact text, font, and position before export.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const formatTemplate = (template, page, total, source) => template
  .replace('{page}', String(page))
  .replace('{total}', String(total))
  .replace('{source}', String(source));

const validateNumericInputs = (start, fontSize, margin) => {
  const startValue = Number(start);
  const sizeValue = Number(fontSize);
  const marginValue = Number(margin);
  if (!Number.isInteger(startValue) || startValue < 0 || startValue > 1_000_000) {
    return 'Start must be an integer between 0 and 1,000,000.';
  }
  if (!Number.isFinite(sizeValue) || sizeValue < 6 || sizeValue > 72) {
    return 'Font size must be between 6 and 72.';
  }
  if (!Number.isFinite(marginValue) || marginValue < 0 || marginValue > 144) {
    return 'Margin must be between 0 and 144 points.';
  }
  return null;
};

const pageNumberStyle = (position, margin, fontSize, font) => {
  const style = {
    fontFamily: font.startsWith('times')
      ? 'Georgia, serif'
      : font.startsWith('courier')
        ? 'ui-monospace, monospace'
        : 'Arial, sans-serif',
    fontSize: `${fontSize}px`,
    fontWeight: font.endsWith('-bold') ? 700 : 400,
  };
  if (position.startsWith('top-')) {
    style.top = `${margin}px`;
  } else {
    style.bottom = `${margin}px`;
  }
  if (position.endsWith('-left')) {
    style.left = `${margin}px`;
  } else if (position.endsWith('-right')) {
    style.right = `${margin}px`;
  } else {
    style.left = '50%';
    style.transform = 'translateX(-50%)';
  }
  return style;
};

export default PageNumbersPage;
