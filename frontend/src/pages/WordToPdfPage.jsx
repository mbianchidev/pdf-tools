import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ArrowLeft,
  Download,
  FileType,
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
import './WordToPdfPage.css';

const WORD_ACCEPT = {
  'application/msword': ['.doc'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': [
    '.docx',
  ],
};

const WordToPdfPage = () => {
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
      addToast('Converted PDF download started!', 'success');
    } catch (error) {
      console.error('Word-to-PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download converted PDF'),
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
        addToast(
          job.errorMessage || 'Failed to convert Word document',
          'error',
        );
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('Word conversion cancelled', 'error');
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
  }, [addToast, downloadOutput, job]);

  const handleFilesChange = useCallback((files) => {
    if (running) return;
    setFile(files[0] || null);
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handleSubmit = async () => {
    if (!file) {
      addToast('Upload a DOCX or DOC file first.', 'error');
      return;
    }
    try {
      await start('word-to-pdf', [file], {});
    } catch (error) {
      console.error('Word-to-PDF job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start Word conversion'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Word-to-PDF cancellation error:', error);
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
          <FileType size={28} />
          <h1>Word to PDF</h1>
        </div>
        <p className="operation-description">
          Convert DOCX and DOC files with an isolated LibreOffice worker.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload Word document</h3>
            <FileUpload
              onFilesChange={handleFilesChange}
              files={file ? [file] : []}
              accept={WORD_ACCEPT}
              multiple={false}
              disabled={running}
              hint="Supports DOCX and DOC files"
            />
          </div>

          <div className="sidebar-section word-security-note">
            <ShieldCheck size={20} />
            <div>
              <h3>Isolated conversion</h3>
              <p>
                LibreOffice runs without network access, with a private
                profile, restricted files, and bounded resources.
              </p>
            </div>
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
              disabled={running || !file}
              icon={<Download size={20} />}
              fullWidth
              size="lg"
            >
              {running ? 'Converting Word...' : 'Convert Word to PDF'}
            </Button>
          </div>
        </aside>

        <main className="operation-preview">
          <div className="word-convert-preview">
            <span className="word-convert-preview__icon">
              <FileType size={56} />
            </span>
            <div>
              <p className="word-convert-preview__eyebrow">LibreOffice Writer</p>
              <h2>Preserve document layout</h2>
              <p>
                Paragraphs, tables, embedded images, headers, footers, and
                pagination are converted using the fonts installed on this
                server.
              </p>
            </div>
            <div className="word-convert-preview__limit">
              Fonts unavailable on the server may be substituted, which can
              change line breaks or pagination.
            </div>
          </div>
        </main>
      </div>
    </div>
  );
};

export default WordToPdfPage;
