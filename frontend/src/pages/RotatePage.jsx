import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ArrowLeft, Download, RotateCw, Undo2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import FileUpload from '../components/FileUpload';
import ToastContainer from '../components/Toast';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { usePdfJob } from '../features/jobs/usePdfJob';
import PageThumbnailOrganizer from '../features/editor/PageThumbnailOrganizer';
import '../features/editor/PageOrganizerWorkbench.css';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './RotatePage.css';

const ROTATIONS = [90, 180, 270];

const RotatePage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [pages, setPages] = useState([]);
  const [selectedPageId, setSelectedPageId] = useState(null);
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

  const instructions = useMemo(() => ROTATIONS.flatMap((rotation) => {
    const selected = pages
      .filter((page) => page.rotation === rotation)
      .map((page) => page.sourcePage);
    return selected.length
      ? [{ pages: selected.join(','), rotation }]
      : [];
  }), [pages]);

  const rotatedPageCount = useMemo(
    () => pages.filter((page) => page.rotation !== 0).length,
    [pages],
  );

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Rotated PDF download started!', 'success');
    } catch (error) {
      console.error('Rotate PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download rotated PDF'),
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
        addToast(job.errorMessage || 'Failed to rotate PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF rotation cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The rotation job completed without an output.', 'error');
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
    setPages([]);
    setSelectedPageId(null);
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const rotateAll = (degrees) => {
    if (running || pages.length === 0) return;
    setPages((current) => current.map((page) => ({
      ...page,
      rotation: (page.rotation + degrees) % 360,
    })));
  };

  const resetRotations = () => {
    if (!running) {
      setPages((current) => current.map((page) => ({
        ...page,
        rotation: 0,
      })));
    }
  };

  const handleRotate = async () => {
    if (!file || instructions.length === 0) {
      addToast('Rotate at least one page before starting.', 'error');
      return;
    }
    try {
      await start('rotate', [file], { rotations: instructions });
    } catch (error) {
      console.error('Rotate PDF job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF rotation'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Rotate PDF cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <RotateCw size={28} />
          <h1>Rotate PDF</h1>
        </div>
        <p className="operation-description">
          Rotate every page together or adjust individual pages before export.
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

          {file && pages.length > 0 && (
            <>
              <div className="sidebar-section">
                <h3 className="sidebar-title">Rotate all pages</h3>
                <div className="rotate-all-actions">
                  {ROTATIONS.map((rotation) => (
                    <button
                      type="button"
                      key={rotation}
                      onClick={() => rotateAll(rotation)}
                      disabled={running}
                      aria-label={`Rotate all pages ${rotation} degrees`}
                    >
                      <RotateCw size={17} aria-hidden="true" />
                      {rotation}°
                    </button>
                  ))}
                </div>
                <div className="rotate-summary">
                  <strong>{rotatedPageCount}</strong>
                  <span>of {pages.length} pages changed</span>
                  <button
                    type="button"
                    onClick={resetRotations}
                    disabled={running || rotatedPageCount === 0}
                  >
                    <Undo2 size={15} aria-hidden="true" />
                    Reset
                  </button>
                </div>
                <p className="rotate-help">
                  Use the rotate control on any page card for an independent
                  adjustment.
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
                  onClick={handleRotate}
                  loading={running}
                  disabled={running || instructions.length === 0}
                  icon={<RotateCw size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Rotating...' : 'Rotate & Download'}
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
                  {pages.length > 0 && ` (${pages.length} pages)`}
                </h3>
                <span className="preview-hint">
                  Per-page controls update the export preview
                </span>
              </div>
              <div className="page-organizer-workbench rotate-organizer">
                <PageThumbnailOrganizer
                  file={file}
                  pages={pages}
                  onPagesChange={setPages}
                  selectedPageId={selectedPageId}
                  onSelectPage={setSelectedPageId}
                  allowMove={false}
                  allowDuplicate={false}
                  allowDelete={false}
                  allowRotate
                  disabled={running}
                  maxPages={1000}
                  onError={(message) => addToast(message, 'error')}
                />
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <RotateCw size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Rotate the entire document or tune pages individually.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default RotatePage;
