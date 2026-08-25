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
  LockKeyhole,
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

const DEFAULT_PERMISSIONS = {
  print: 'none',
  copy: false,
  modify: false,
  annotate: false,
  fillForms: false,
  accessibility: true,
  assemble: false,
};

const ProtectPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [userPassword, setUserPassword] = useState('');
  const [confirmUserPassword, setConfirmUserPassword] = useState('');
  const [ownerPassword, setOwnerPassword] = useState('');
  const [confirmOwnerPassword, setConfirmOwnerPassword] = useState('');
  const [permissions, setPermissions] = useState(DEFAULT_PERMISSIONS);
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

  const passwordError = useMemo(() => validatePasswords(
    userPassword,
    confirmUserPassword,
    ownerPassword,
    confirmOwnerPassword,
  ), [
    confirmOwnerPassword,
    confirmUserPassword,
    ownerPassword,
    userPassword,
  ]);

  const enabledPermissions = useMemo(
    () => Object.entries(permissions)
      .filter(([key, value]) => key === 'print'
        ? value !== 'none'
        : value)
      .map(([key]) => key),
    [permissions],
  );

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Protected PDF download started!', 'success');
    } catch (error) {
      console.error('Protect PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download protected PDF'),
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
        addToast(job.errorMessage || 'Failed to protect PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF protection cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The protection job completed without an output.', 'error');
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
    setUserPassword('');
    setConfirmUserPassword('');
    setOwnerPassword('');
    setConfirmOwnerPassword('');
    setPermissions(DEFAULT_PERMISSIONS);
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const updatePermission = (field, value) => {
    setPermissions((current) => ({ ...current, [field]: value }));
  };

  const handleProtect = async () => {
    if (!file || passwordError) {
      addToast(passwordError || 'Upload a PDF before protecting it.', 'error');
      return;
    }
    try {
      await start('protect', [file], {
        userPassword,
        ownerPassword,
        permissions,
      });
    } catch (error) {
      console.error('Protect PDF job failed', {
        status: error.response?.status,
        code: error.response?.data?.code,
        message: error.message,
      });
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF protection'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Protect PDF cancellation error:', error);
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
          <LockKeyhole size={28} />
          <h1>Protect PDF</h1>
        </div>
        <p className="operation-description">
          Encrypt with separate open and owner passwords plus explicit permissions.
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
              <div className="sidebar-section security-fields">
                <h3 className="sidebar-title">Passwords</h3>
                {[
                  ['Open password', userPassword, setUserPassword],
                  [
                    'Confirm open password',
                    confirmUserPassword,
                    setConfirmUserPassword,
                  ],
                  ['Owner password', ownerPassword, setOwnerPassword],
                  [
                    'Confirm owner password',
                    confirmOwnerPassword,
                    setConfirmOwnerPassword,
                  ],
                ].map(([label, value, setter]) => (
                  <label key={label}>
                    {label}
                    <input
                      type="password"
                      value={value}
                      onChange={(event) => setter(event.target.value)}
                      autoComplete="new-password"
                      disabled={running}
                    />
                  </label>
                ))}
                <p className={passwordError ? 'security-error' : 'security-help'}>
                  {passwordError
                    || 'Passwords are encrypted before job metadata is stored.'}
                </p>
              </div>

              <div className="sidebar-section security-permissions">
                <h3 className="sidebar-title">User permissions</h3>
                <label>
                  Printing
                  <select
                    value={permissions.print}
                    onChange={(event) => updatePermission(
                      'print',
                      event.target.value,
                    )}
                    disabled={running}
                  >
                    <option value="none">Not allowed</option>
                    <option value="low">Low resolution</option>
                    <option value="high">High resolution</option>
                  </select>
                </label>
                {[
                  ['copy', 'Allow copying'],
                  ['modify', 'Allow document changes'],
                  ['annotate', 'Allow annotations'],
                  ['fillForms', 'Allow form filling'],
                  ['accessibility', 'Allow accessibility extraction'],
                  ['assemble', 'Allow document assembly'],
                ].map(([field, label]) => (
                  <label className="permission-check" key={field}>
                    <input
                      type="checkbox"
                      checked={permissions[field]}
                      onChange={(event) => updatePermission(
                        field,
                        event.target.checked,
                      )}
                      disabled={running}
                    />
                    {label}
                  </label>
                ))}
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
                  onClick={handleProtect}
                  loading={running}
                  disabled={!canSubmit}
                  icon={<LockKeyhole size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Encrypting...' : 'Protect & Download'}
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
                <span>AES-256 encrypted PDF</span>
                <div className="security-document__status">
                  <ShieldCheck size={18} aria-hidden="true" />
                  {userPassword && ownerPassword
                    ? 'Both passwords set'
                    : 'Set both passwords'}
                </div>
              </div>
              <section className="security-policy">
                <p>Open password required</p>
                <p>Separate owner controls</p>
                <p>
                  {enabledPermissions.length} user permission
                  {enabledPermissions.length === 1 ? '' : 's'} enabled
                </p>
                <p>Sensitive job options encrypted at rest</p>
              </section>
            </div>
          ) : (
            <div className="preview-empty">
              <LockKeyhole size={64} />
              <h3>Upload a PDF to protect</h3>
              <p>Choose passwords and least-privilege user permissions.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const validatePasswords = (
  userPassword,
  confirmUserPassword,
  ownerPassword,
  confirmOwnerPassword,
) => {
  if (!userPassword || !ownerPassword) {
    return 'Both open and owner passwords are required.';
  }
  if (userPassword !== confirmUserPassword) {
    return 'Open passwords do not match.';
  }
  if (ownerPassword !== confirmOwnerPassword) {
    return 'Owner passwords do not match.';
  }
  if (userPassword === ownerPassword) {
    return 'Open and owner passwords must differ.';
  }
  const printableAscii = /^[\x20-\x7e]+$/;
  if (!printableAscii.test(userPassword)
      || !printableAscii.test(ownerPassword)) {
    return 'Passwords must contain printable ASCII characters only.';
  }
  if (new TextEncoder().encode(userPassword).length > 127
      || new TextEncoder().encode(ownerPassword).length > 127) {
    return 'Passwords must stay within 127 UTF-8 bytes.';
  }
  return null;
};

export default ProtectPage;
