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
  Crop,
  Download,
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
import './CropPage.css';

const EMPTY_MARGINS = Object.freeze({
  top: 0,
  right: 0,
  bottom: 0,
  left: 0,
});

const CropPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [mode, setMode] = useState('shared');
  const [sharedMargins, setSharedMargins] = useState(EMPTY_MARGINS);
  const [pageMargins, setPageMargins] = useState({});
  const [toasts, setToasts] = useState([]);
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

  const currentMargins = mode === 'shared'
    ? sharedMargins
    : pageMargins[currentPage] || EMPTY_MARGINS;
  const currentError = validateMargins(currentMargins);
  const currentRectangle = currentError
    ? null
    : rectangleFromMargins(currentMargins);

  const cropPlan = useMemo(() => {
    if (mode === 'shared') {
      const error = validateMargins(sharedMargins);
      return {
        error,
        meaningful: isMeaningful(sharedMargins),
        options: error
          ? null
          : { crop: rectangleFromMargins(sharedMargins) },
      };
    }
    const crops = [];
    for (const [page, margins] of Object.entries(pageMargins)
      .sort(([left], [right]) => Number(left) - Number(right))) {
      const error = validateMargins(margins);
      if (error) {
        return { error, meaningful: false, options: null };
      }
      if (isMeaningful(margins)) {
        crops.push({
          pages: page,
          rectangle: rectangleFromMargins(margins),
        });
      }
    }
    return {
      error: null,
      meaningful: crops.length > 0,
      options: { crops },
    };
  }, [mode, pageMargins, sharedMargins]);

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Cropped PDF download started!', 'success');
    } catch (error) {
      console.error('Crop PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download cropped PDF'),
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
        addToast(job.errorMessage || 'Failed to crop PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF crop cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The crop job completed without an output.', 'error');
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
    setFile(files[0] || null);
    setNumPages(null);
    setCurrentPage(1);
    setMode('shared');
    setSharedMargins(EMPTY_MARGINS);
    setPageMargins({});
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handleDocumentLoad = useCallback(({ numPages: loadedPages }) => {
    setNumPages(loadedPages);
    setCurrentPage(1);
  }, []);

  const updateMargin = (side, value) => {
    const nextValue = value === '' ? 0 : Number(value);
    if (mode === 'shared') {
      setSharedMargins((current) => ({
        ...current,
        [side]: nextValue,
      }));
      return;
    }
    setPageMargins((current) => {
      const next = {
        ...(current[currentPage] || EMPTY_MARGINS),
        [side]: nextValue,
      };
      if (!isMeaningful(next)) {
        const remaining = { ...current };
        delete remaining[currentPage];
        return remaining;
      }
      return { ...current, [currentPage]: next };
    });
  };

  const handleCrop = async () => {
    if (!file || !numPages || !cropPlan.meaningful || cropPlan.error) {
      addToast(
        cropPlan.error || 'Set at least one crop margin before starting.',
        'error',
      );
      return;
    }
    try {
      await start('crop', [file], cropPlan.options);
    } catch (error) {
      console.error('Crop PDF job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF crop'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Crop PDF cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const previewWidth = Math.min(
    650,
    Math.max(280, globalThis.innerWidth - 48),
  );
  const canSubmit = Boolean(
    file
      && numPages
      && cropPlan.meaningful
      && !cropPlan.error
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
          <Crop size={28} />
          <h1>Crop PDF</h1>
        </div>
        <p className="operation-description">
          Apply one visual crop or tune crop boxes page by page.
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
              <div className="sidebar-section">
                <h3 className="sidebar-title">Crop mode</h3>
                <div className="crop-mode-toggle">
                  {[
                    ['shared', 'All pages'],
                    ['per-page', 'Per page'],
                  ].map(([value, label]) => (
                    <button
                      type="button"
                      key={value}
                      className={mode === value ? 'is-active' : ''}
                      onClick={() => setMode(value)}
                      disabled={running}
                    >
                      {label}
                    </button>
                  ))}
                </div>

                {mode === 'per-page' && (
                  <div className="crop-page-navigation">
                    <button
                      type="button"
                      aria-label="Previous page"
                      onClick={() => setCurrentPage((page) => Math.max(
                        page - 1,
                        1,
                      ))}
                      disabled={running || currentPage === 1}
                    >
                      <ChevronLeft size={18} />
                    </button>
                    <span>Page {currentPage} of {numPages}</span>
                    <button
                      type="button"
                      aria-label="Next page"
                      onClick={() => setCurrentPage((page) => Math.min(
                        page + 1,
                        numPages,
                      ))}
                      disabled={running || currentPage === numPages}
                    >
                      <ChevronRight size={18} />
                    </button>
                  </div>
                )}

                <div className="crop-margin-grid">
                  {[
                    ['top', 'Top margin (%)'],
                    ['right', 'Right margin (%)'],
                    ['bottom', 'Bottom margin (%)'],
                    ['left', 'Left margin (%)'],
                  ].map(([side, label]) => (
                    <label key={side}>
                      {label}
                      <input
                        type="number"
                        min="0"
                        max="99"
                        step="1"
                        value={currentMargins[side]}
                        onChange={(event) => updateMargin(
                          side,
                          event.target.value,
                        )}
                        disabled={running}
                      />
                    </label>
                  ))}
                </div>
                <p className={currentError ? 'crop-error' : 'crop-help'}>
                  {currentError || (
                    mode === 'shared'
                      ? 'The outlined area will be kept on every page.'
                      : `The outlined area applies only to page ${currentPage}.`
                  )}
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
                  onClick={handleCrop}
                  loading={running}
                  disabled={!canSubmit}
                  icon={<Crop size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Cropping...' : 'Crop & Download'}
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
                <span className="preview-hint">
                  The shaded area is removed from the visible page
                </span>
              </div>
              <div className="crop-preview">
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
                  <div className="crop-preview-page">
                    <Page
                      pageNumber={currentPage}
                      width={previewWidth}
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                    />
                    {currentRectangle && (
                      <div
                        className="crop-selection"
                        style={normalizedRectangleStyle(currentRectangle)}
                        aria-label="Crop area kept"
                      />
                    )}
                  </div>
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Crop size={64} />
              <h3>Upload a PDF to crop</h3>
              <p>Define the exact visible area with normalized margins.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const validateMargins = ({
  top,
  right,
  bottom,
  left,
}) => {
  const values = [top, right, bottom, left];
  if (values.some((value) => !Number.isFinite(value)
      || value < 0
      || value > 99.9)) {
    return 'Margins must be between 0 and 99.9 percent.';
  }
  if (left + right > 99.9) {
    return 'Horizontal margins must leave visible content.';
  }
  if (top + bottom > 99.9) {
    return 'Vertical margins must leave visible content.';
  }
  return null;
};

const rectangleFromMargins = ({
  top,
  right,
  bottom,
  left,
}) => ({
  x: quantize(left / 100),
  y: quantize(top / 100),
  width: quantize((100 - left - right) / 100),
  height: quantize((100 - top - bottom) / 100),
});

const isMeaningful = (margins) => Object.values(margins)
  .some((value) => value > 0);

const quantize = (value) => Math.round(value * 1_000_000) / 1_000_000;

export default CropPage;
