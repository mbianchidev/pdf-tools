import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  EyeOff,
  ShieldCheck,
  Trash2,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Document, Page } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import '../lib/pdfWorker';
import Button from '../components/Button';
import FileUpload from '../components/FileUpload';
import ToastContainer from '../components/Toast';
import { normalizedRectangleStyle } from '../features/editor/coordinates';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './RedactPage.css';

const MIN_DRAW_PIXELS = 4;

const RedactPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(null);
  const [areas, setAreas] = useState([]);
  const [draft, setDraft] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [toasts, setToasts] = useState([]);
  const sequenceRef = useRef(0);
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

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Securely redacted PDF download started!', 'success');
    } catch (error) {
      console.error('Redact PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download redacted PDF'),
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
        addToast(job.errorMessage || 'Failed to redact PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('Secure redaction cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The redaction job completed without an output.', 'error');
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
    setSelectedId(null);
    setDraft(null);
    await loadPageSize(next, documentRef.current);
  };

  const handleFilesChange = useCallback((files) => {
    if (running) return;
    setFile(files[0] || null);
    setNumPages(null);
    setCurrentPage(1);
    setPageSize(null);
    setAreas([]);
    setDraft(null);
    setSelectedId(null);
    documentRef.current = null;
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const beginArea = (event) => {
    if (running || !pageSize) return;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    const point = normalizedPoint(event);
    setSelectedId(null);
    setDraft({
      pointerId: event.pointerId,
      anchorX: point.x,
      anchorY: point.y,
      x: point.x,
      y: point.y,
      width: 0,
      height: 0,
    });
  };

  const resizeArea = (event) => {
    if (!draft || draft.pointerId !== event.pointerId) return;
    const point = normalizedPoint(event);
    setDraft((current) => rectangleFromPoints(
      current,
      point,
    ));
  };

  const finishArea = (event) => {
    if (!draft || draft.pointerId !== event.pointerId) return;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    const rectangle = rectangleFromPoints(draft, normalizedPoint(event));
    setDraft(null);
    if (
      rectangle.width * renderWidth < MIN_DRAW_PIXELS
      || rectangle.height * renderHeight < MIN_DRAW_PIXELS
    ) {
      return;
    }
    const area = {
      id: `redaction-${sequenceRef.current}`,
      page: currentPage,
      x: quantize(rectangle.x),
      y: quantize(rectangle.y),
      width: quantize(rectangle.width),
      height: quantize(rectangle.height),
    };
    sequenceRef.current += 1;
    setAreas((current) => [...current, area]);
    setSelectedId(area.id);
    handledJobRef.current = null;
    reset();
  };

  const removeArea = (id) => {
    setAreas((current) => current.filter((area) => area.id !== id));
    if (selectedId === id) {
      setSelectedId(null);
    }
    handledJobRef.current = null;
    reset();
  };

  const selectArea = async (area) => {
    setSelectedId(area.id);
    if (area.page !== currentPage) {
      await goToPage(area.page);
      setSelectedId(area.id);
    }
  };

  const handleSubmit = async () => {
    if (!file || !numPages || areas.length === 0) {
      addToast('Draw at least one redaction area.', 'error');
      return;
    }
    const submittedAreas = areas
      .map(({ id, ...area }) => area)
      .sort(areaOrder);
    try {
      await start('redact', [file], { areas: submittedAreas });
    } catch (error) {
      console.error('Redact PDF job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start secure redaction'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Redact PDF cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const renderWidth = Math.min(
    620,
    Math.max(280, globalThis.innerWidth - 48),
  );
  const renderHeight = pageSize
    ? renderWidth * pageSize.height / pageSize.width
    : 0;
  const currentAreas = areas.filter((area) => area.page === currentPage);

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <EyeOff size={28} />
          <h1>Redact PDF</h1>
        </div>
        <p className="operation-description">
          Permanently remove sensitive regions from a sanitized PDF.
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
              <div className="sidebar-section redact-security-note">
                <ShieldCheck size={20} />
                <div>
                  <h3>Irreversible output</h3>
                  <p>
                    Every page is rasterized into a new PDF. Searchable text,
                    links, forms, and original objects are removed.
                  </p>
                </div>
              </div>

              <div className="sidebar-section redact-areas">
                <div className="redact-areas__heading">
                  <h3 className="sidebar-title">Redaction areas</h3>
                  <span>
                    {areas.length} {areas.length === 1 ? 'area' : 'areas'}
                  </span>
                </div>
                <p className="redact-help">
                  Drag across the preview to mark content for removal.
                </p>
                {areas.length === 0 ? (
                  <p className="redact-empty">No redaction areas yet</p>
                ) : (
                  <div className="redact-area-list">
                    {areas.map((area, index) => (
                      <div
                        className={area.id === selectedId ? 'selected' : ''}
                        key={area.id}
                      >
                        <button
                          type="button"
                          className="redact-area-select"
                          onClick={() => selectArea(area)}
                          disabled={running}
                        >
                          <span>
                            <strong>Area {index + 1}</strong>
                            <small>
                              Page {area.page} · {percent(area.width)}
                              {' × '}
                              {percent(area.height)}
                            </small>
                          </span>
                        </button>
                        <button
                          type="button"
                          className="redact-area-delete"
                          aria-label={`Delete redaction area ${index + 1}`}
                          onClick={() => removeArea(area.id)}
                          disabled={running}
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    ))}
                  </div>
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
                  disabled={running || areas.length === 0}
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running
                    ? 'Redacting securely...'
                    : 'Redact securely & Download'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <span>Page {currentPage} of {numPages || '?'}</span>
                <div className="page-number-navigation">
                  <button
                    type="button"
                    onClick={() => goToPage(currentPage - 1)}
                    disabled={running || currentPage <= 1}
                    aria-label="Previous page"
                  >
                    <ChevronLeft size={20} />
                  </button>
                  <button
                    type="button"
                    onClick={() => goToPage(currentPage + 1)}
                    disabled={running || currentPage >= (numPages || 1)}
                    aria-label="Next page"
                  >
                    <ChevronRight size={20} />
                  </button>
                </div>
              </div>
              <div className="redact-preview">
                <Document
                  file={file}
                  onLoadSuccess={handleDocumentLoad}
                  loading={<p>Loading secure redaction preview...</p>}
                >
                  <div
                    className="redact-canvas"
                    role="application"
                    aria-label="Redaction canvas"
                    style={{
                      width: renderWidth,
                      height: renderHeight || 'auto',
                    }}
                    onPointerDown={beginArea}
                    onPointerMove={resizeArea}
                    onPointerUp={finishArea}
                    onPointerCancel={() => setDraft(null)}
                  >
                    <Page
                      pageNumber={currentPage}
                      width={renderWidth}
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                    />
                    {currentAreas.map((area, index) => (
                      <button
                        type="button"
                        className={[
                          'redact-box',
                          area.id === selectedId ? 'selected' : '',
                        ].join(' ')}
                        style={normalizedRectangleStyle(area)}
                        onPointerDown={(event) => event.stopPropagation()}
                        onClick={() => setSelectedId(area.id)}
                        aria-label={`Select redaction area ${index + 1}`}
                        disabled={running}
                        key={area.id}
                      />
                    ))}
                    {draft && (
                      <span
                        className="redact-box drawing"
                        style={normalizedRectangleStyle(draft)}
                      />
                    )}
                  </div>
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <EyeOff size={64} />
              <h3>Upload a PDF to redact</h3>
              <p>Draw black boxes over content that must be removed.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const normalizedPoint = (event) => {
  const bounds = event.currentTarget.getBoundingClientRect();
  return {
    x: clamp((event.clientX - bounds.left) / bounds.width),
    y: clamp((event.clientY - bounds.top) / bounds.height),
  };
};

const rectangleFromPoints = (start, end) => ({
  ...start,
  x: Math.min(start.anchorX, end.x),
  y: Math.min(start.anchorY, end.y),
  width: Math.abs(end.x - start.anchorX),
  height: Math.abs(end.y - start.anchorY),
});

const areaOrder = (left, right) => (
  left.page - right.page
  || left.x - right.x
  || left.y - right.y
  || left.width - right.width
  || left.height - right.height
);

const quantize = (value) => Number(value.toFixed(6));

const percent = (value) => `${Math.round(value * 100)}%`;

const clamp = (value) => Math.min(Math.max(value, 0), 1);

export default RedactPage;
