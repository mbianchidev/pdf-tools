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
  FileKey2,
  KeyRound,
  LockOpen,
  ShieldCheck,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import FileUpload from '../components/FileUpload';
import ToastContainer from '../components/Toast';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './SecurityPage.css';

const UnlockPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
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

  const passwordError = useMemo(
    () => validatePassword(password, confirmPassword),
    [confirmPassword, password],
  );

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Unlocked PDF download started!', 'success');
    } catch (error) {
      console.error('Unlock PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download unlocked PDF'),
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
        addToast(job.errorMessage || 'Failed to unlock PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF unlock cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The unlock job completed without an output.', 'error');
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
    setPassword('');
    setConfirmPassword('');
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handleUnlock = async () => {
    if (!file || passwordError) {
      addToast(passwordError || 'Upload a PDF before unlocking it.', 'error');
      return;
    }
    try {
      await start('unlock', [file], { password });
    } catch (error) {
      console.error('Unlock PDF job failed', {
        status: error.response?.status,
        code: error.response?.data?.code,
        message: error.message,
      });
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF unlock'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Unlock PDF cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const canSubmit = Boolean(file && !passwordError && !running);

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <LockOpen size={28} />
          <h1>Unlock PDF</h1>
        </div>
        <p className="operation-description">
          Remove password encryption when you know the current password.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload protected PDF</h3>
            <FileUpload
              onFilesChange={handleFilesChange}
              files={file ? [file] : []}
              multiple={false}
              disabled={running}
            />
          </div>

          {file && (
            <>
              <div className="sidebar-section security-fields">
                <h3 className="sidebar-title">Current password</h3>
                <label>
                  Current password
                  <input
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    autoComplete="current-password"
                    disabled={running}
                  />
                </label>
                <label>
                  Confirm password
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    autoComplete="current-password"
                    disabled={running}
                  />
                </label>
                <p className={passwordError ? 'security-error' : 'security-help'}>
                  {passwordError
                    || 'The password is encrypted before job metadata is stored.'}
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
                  onClick={handleUnlock}
                  loading={running}
                  disabled={!canSubmit}
                  icon={<LockOpen size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Removing encryption...' : 'Unlock & Download'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <div className="security-preview">
              <div className="security-document">
                <FileKey2 size={48} aria-hidden="true" />
                <strong>{file.name}</strong>
                <span>Password-protected PDF</span>
                <div className="security-document__status">
                  {password ? (
                    <ShieldCheck size={18} aria-hidden="true" />
                  ) : (
                    <KeyRound size={18} aria-hidden="true" />
                  )}
                  {password ? 'Password entered' : 'Password required'}
                </div>
              </div>
              <section className="security-policy">
                <p>User or owner password accepted</p>
                <p>Document content preserved</p>
                <p>Encryption dictionary removed</p>
                <p>Sensitive job options encrypted at rest</p>
              </section>
            </div>
          ) : (
            <div className="preview-empty">
              <LockOpen size={64} />
              <h3>Upload a protected PDF</h3>
              <p>Enter its current user or owner password to remove encryption.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const validatePassword = (password, confirmation) => {
  if (!password) {
    return 'The current PDF password is required.';
  }
  if (password !== confirmation) {
    return 'Passwords do not match.';
  }
  if (new TextEncoder().encode(password).length > 127) {
    return 'Password must stay within 127 UTF-8 bytes.';
  }
  return null;
};

export default UnlockPage;
