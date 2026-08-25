import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ArrowLeft,
  Download,
  ShieldCheck,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../../components/Button';
import FileUpload from '../../components/FileUpload';
import ToastContainer from '../../components/Toast';
import JobProgress from '../jobs/JobProgress';
import { startBrowserDownload } from '../jobs/startBrowserDownload';
import { usePdfJob } from '../jobs/usePdfJob';
import { getApiErrorMessage, jobService } from '../../services/jobService';
import '../../pages/OperationPage.css';
import './OfficeToPdfPage.css';

const OfficeToPdfPage = ({
  operation,
  title,
  description,
  Icon,
  uploadTitle,
  accept,
  hint,
  actionLabel,
  runningLabel,
  successMessage,
  previewEyebrow,
  previewTitle,
  previewDescription,
  fidelityWarning,
  securityTitle = 'Isolated conversion',
  securityDescription = 'LibreOffice runs in a networkless sidecar under a separate non-root identity with private scratch and bounded resources.',
  options = {},
  renderControls,
  renderResult,
  validationError,
}) => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
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

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast(successMessage, 'success');
    } catch (error) {
      console.error(`${title} download error:`, error);
      addToast(
        getApiErrorMessage(error, 'Failed to download converted PDF'),
        'error',
      );
    }
  }, [addToast, successMessage, title]);

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
        addToast(job.errorMessage || `Failed to run ${title}`, 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast(`${title} cancelled`, 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The conversion completed without an output.', 'error');
        return;
      }
      downloadOutput(output);
    };
    handleResult();
    return () => {
      active = false;
    };
  }, [addToast, downloadOutput, job, title]);

  const handleFilesChange = useCallback((files) => {
    if (running) return;
    setFile(files[0] || null);
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handleSubmit = async () => {
    if (!file) {
      addToast(`Upload ${hint.toLowerCase()} first.`, 'error');
      return;
    }
    if (validationError) {
      addToast(validationError, 'error');
      return;
    }
    try {
      await start(operation, [file], options);
    } catch (error) {
      console.error(`${title} job error:`, error);
      addToast(
        getApiErrorMessage(error, `Failed to start ${title}`),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error(`${title} cancellation error:`, error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  return (
    <div className="operation-page isolated-conversion-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <Icon size={28} />
          <h1>{title}</h1>
        </div>
        <p className="operation-description">{description}</p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">{uploadTitle}</h3>
            <FileUpload
              onFilesChange={handleFilesChange}
              files={file ? [file] : []}
              accept={accept}
              multiple={false}
              disabled={running}
              hint={hint}
            />
          </div>

          <div className="sidebar-section office-security-note">
            <ShieldCheck size={20} />
            <div>
              <h3>{securityTitle}</h3>
              <p>{securityDescription}</p>
            </div>
          </div>

          {renderControls?.({ running })}
          {renderResult?.({ job, file })}

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
              disabled={running || !file || Boolean(validationError)}
              icon={<Download size={20} />}
              fullWidth
              size="lg"
            >
              {running ? runningLabel : actionLabel}
            </Button>
          </div>
        </aside>

        <main className="operation-preview">
          <div className="office-convert-preview">
            <span className="office-convert-preview__icon">
              <Icon size={56} />
            </span>
            <div>
              <p className="office-convert-preview__eyebrow">
                {previewEyebrow}
              </p>
              <h2>{previewTitle}</h2>
              <p>{previewDescription}</p>
            </div>
            <div className="office-convert-preview__limit">
              {fidelityWarning}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};

export default OfficeToPdfPage;
