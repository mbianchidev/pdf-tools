import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  ArrowLeft,
  Download,
  Layers3,
  RotateCw,
  Undo2,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import FileUpload from '../components/FileUpload';
import ToastContainer from '../components/Toast';
import PageThumbnailOrganizer from '../features/editor/PageThumbnailOrganizer';
import '../features/editor/PageOrganizerWorkbench.css';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './OrganizePage.css';

const OrganizePage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [pages, setPages] = useState([]);
  const [initialPages, setInitialPages] = useState([]);
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

  const stats = useMemo(() => {
    const uniqueSources = new Set(pages.map((page) => page.sourcePage));
    return {
      deleted: Math.max(initialPages.length - uniqueSources.size, 0),
      duplicated: Math.max(pages.length - uniqueSources.size, 0),
      rotated: pages.filter((page) => page.rotation !== 0).length,
    };
  }, [initialPages.length, pages]);

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Organized PDF download started!', 'success');
    } catch (error) {
      console.error('Organize PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download organized PDF'),
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
        addToast(job.errorMessage || 'Failed to organize PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF organization cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The organize job completed without an output.', 'error');
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
    setInitialPages([]);
    setSelectedPageId(null);
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handlePagesChange = useCallback((nextPages) => {
    setPages(nextPages);
    setInitialPages((current) => (
      current.length === 0
        ? nextPages.map((page) => ({ ...page }))
        : current
    ));
  }, []);

  const resetPlan = () => {
    if (running || initialPages.length === 0) return;
    const restored = initialPages.map((page) => ({ ...page }));
    setPages(restored);
    setSelectedPageId(restored[0]?.id ?? null);
  };

  const handleOrganize = async () => {
    if (!file || pages.length === 0) {
      addToast('Upload a PDF with at least one page.', 'error');
      return;
    }
    try {
      await start('organize', [file], {
        pages: pages.map((page) => ({
          page: page.sourcePage,
          rotation: page.rotation,
        })),
      });
    } catch (error) {
      console.error('Organize PDF job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF organization'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Organize PDF cancellation error:', error);
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
          <Layers3 size={28} />
          <h1>Organize PDF</h1>
        </div>
        <p className="operation-description">
          Reorder, rotate, duplicate, and delete pages in one visual workflow.
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
                <h3 className="sidebar-title">Output plan</h3>
                <div className="organize-stats">
                  <span><strong>{pages.length}</strong> output pages</span>
                  <span><strong>{stats.rotated}</strong> rotated</span>
                  <span><strong>{stats.duplicated}</strong> duplicated</span>
                  <span><strong>{stats.deleted}</strong> deleted</span>
                </div>
                <button
                  type="button"
                  className="organize-reset"
                  onClick={resetPlan}
                  disabled={running}
                >
                  <Undo2 size={16} aria-hidden="true" />
                  Reset original order
                </button>
                <p className="organize-help">
                  Drag cards or use arrow controls. Each card also exposes
                  rotate, duplicate, and delete actions.
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
                  onClick={handleOrganize}
                  loading={running}
                  disabled={running || pages.length === 0}
                  icon={<Layers3 size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Organizing...' : 'Organize & Download'}
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
                  {pages.length > 0 && ` (${pages.length} output pages)`}
                </h3>
                <span className="preview-hint">
                  <RotateCw size={14} aria-hidden="true" />
                  Changes are applied in the displayed order
                </span>
              </div>
              <div className="page-organizer-workbench organize-workbench">
                <PageThumbnailOrganizer
                  file={file}
                  pages={pages}
                  onPagesChange={handlePagesChange}
                  selectedPageId={selectedPageId}
                  onSelectPage={setSelectedPageId}
                  disabled={running}
                  maxPages={1000}
                  onError={(message) => addToast(message, 'error')}
                />
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Layers3 size={64} />
              <h3>Upload a PDF to organize</h3>
              <p>Build the exact page sequence you want to export.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default OrganizePage;
