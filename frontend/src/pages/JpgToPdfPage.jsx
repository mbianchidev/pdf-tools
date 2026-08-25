import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Download,
  FileImage,
  GripVertical,
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
import './JpgToPdfPage.css';

const MAX_IMAGES = 100;
const WINDOW_SIZE = 20;

const JpgToPdfPage = () => {
  const navigate = useNavigate();
  const [images, setImages] = useState([]);
  const [pageSize, setPageSize] = useState('fit');
  const [orientation, setOrientation] = useState('auto');
  const [margin, setMargin] = useState(24);
  const [windowIndex, setWindowIndex] = useState(0);
  const [dragIndex, setDragIndex] = useState(null);
  const [toasts, setToasts] = useState([]);
  const sequenceRef = useRef(0);
  const urlsRef = useRef(new Set());
  const handledJobRef = useRef(null);
  const {
    job,
    running,
    connectionError,
    start,
    cancel,
    reset,
  } = usePdfJob();

  useEffect(() => () => {
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url));
    urlsRef.current.clear();
  }, []);

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
      addToast('PDF download started!', 'success');
    } catch (error) {
      console.error('JPG to PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download generated PDF'),
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
        addToast(job.errorMessage || 'Failed to convert JPG files', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('JPG to PDF conversion cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The conversion completed without a PDF output.', 'error');
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
    const accepted = files.slice(0, MAX_IMAGES);
    if (files.length > MAX_IMAGES) {
      addToast(`Use at most ${MAX_IMAGES} JPG images.`, 'error');
    }
    setImages((current) => {
      const used = new Set();
      const next = accepted.map((file) => {
        const existing = current.find(
          (image) => image.file === file && !used.has(image.id),
        );
        if (existing) {
          used.add(existing.id);
          return existing;
        }
        const url = URL.createObjectURL(file);
        urlsRef.current.add(url);
        const created = {
          id: `jpg-${sequenceRef.current}`,
          file,
          url,
        };
        sequenceRef.current += 1;
        used.add(created.id);
        return created;
      });
      current
        .filter((image) => !used.has(image.id))
        .forEach((image) => {
          URL.revokeObjectURL(image.url);
          urlsRef.current.delete(image.url);
        });
      return next;
    });
    setWindowIndex(0);
    handledJobRef.current = null;
    reset();
  }, [addToast, reset, running]);

  const reorder = (from, to) => {
    if (running || from === to || to < 0 || to >= images.length) return;
    setImages((current) => {
      const next = [...current];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
    handledJobRef.current = null;
    reset();
  };

  const handleDrop = (target) => {
    if (dragIndex !== null) {
      reorder(dragIndex, target);
    }
    setDragIndex(null);
  };

  const marginError = validateMargin(margin);

  const handleSubmit = async () => {
    if (images.length === 0 || marginError) {
      addToast(
        marginError || 'Upload at least one JPG image.',
        'error',
      );
      return;
    }
    try {
      await start(
        'jpg-to-pdf',
        images.map((image) => image.file),
        {
          pageSize,
          orientation,
          margin: Number(margin),
        },
      );
    } catch (error) {
      console.error('JPG to PDF job failed:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start JPG conversion'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('JPG to PDF cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const pageCount = Math.max(Math.ceil(images.length / WINDOW_SIZE), 1);
  const currentWindow = Math.min(windowIndex, pageCount - 1);
  const startIndex = currentWindow * WINDOW_SIZE;
  const visibleImages = images.slice(
    startIndex,
    startIndex + WINDOW_SIZE,
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
          <FileImage size={28} />
          <h1>JPG to PDF</h1>
        </div>
        <p className="operation-description">
          Order JPG images and place them on consistent PDF pages.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload JPG images</h3>
            <FileUpload
              onFilesChange={handleFilesChange}
              files={images.map((image) => image.file)}
              accept={{ 'image/jpeg': ['.jpg', '.jpeg'] }}
              multiple
              maxFiles={MAX_IMAGES}
              disabled={running}
              hint={`Supports up to ${MAX_IMAGES} JPG images`}
            />
          </div>

          {images.length > 0 && (
            <>
              <div className="sidebar-section jpg-pdf-controls">
                <h3 className="sidebar-title">Page settings</h3>
                <label>
                  Page size
                  <select
                    value={pageSize}
                    onChange={(event) => setPageSize(event.target.value)}
                    disabled={running}
                  >
                    <option value="fit">Fit each image</option>
                    <option value="a4">A4</option>
                    <option value="letter">US Letter</option>
                    <option value="legal">US Legal</option>
                  </select>
                </label>
                <label>
                  Orientation
                  <select
                    value={orientation}
                    onChange={(event) => setOrientation(event.target.value)}
                    disabled={running}
                  >
                    <option value="auto">Auto per image</option>
                    <option value="portrait">Portrait</option>
                    <option value="landscape">Landscape</option>
                  </select>
                </label>
                <label>
                  Margin
                  <input
                    aria-label="Margin"
                    type="number"
                    min="0"
                    max="144"
                    value={margin}
                    onChange={(event) => setMargin(event.target.value)}
                    disabled={running}
                  />
                  <span className={marginError ? 'is-error' : ''}>
                    {marginError || 'Points on every side'}
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
                  disabled={running || Boolean(marginError)}
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Building PDF...' : 'Create & Download PDF'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {images.length > 0 ? (
            <div className="jpg-pdf-workbench">
              {images.length > WINDOW_SIZE && (
                <nav aria-label="Image groups" className="jpg-pdf-pagination">
                  <button
                    type="button"
                    onClick={() => setWindowIndex(currentWindow - 1)}
                    disabled={running || currentWindow === 0}
                  >
                    Previous images
                  </button>
                  <span>
                    Images {startIndex + 1}-
                    {Math.min(startIndex + WINDOW_SIZE, images.length)}
                    {' '}of {images.length}
                  </span>
                  <button
                    type="button"
                    onClick={() => setWindowIndex(currentWindow + 1)}
                    disabled={running || currentWindow >= pageCount - 1}
                  >
                    Next images
                  </button>
                </nav>
              )}
              <ol className="jpg-pdf-grid" aria-label="PDF page order">
                {visibleImages.map((image, visibleIndex) => {
                  const index = startIndex + visibleIndex;
                  return (
                    <li
                      key={image.id}
                      draggable={!running}
                      onDragStart={() => setDragIndex(index)}
                      onDragOver={(event) => !running
                        && event.preventDefault()}
                      onDrop={() => handleDrop(index)}
                    >
                      <div className="jpg-pdf-thumb">
                        <GripVertical size={16} aria-hidden="true" />
                        <img
                          src={image.url}
                          alt=""
                          loading="lazy"
                          decoding="async"
                        />
                        <span>Page {index + 1}</span>
                      </div>
                      <strong title={image.file.name}>
                        {image.file.name}
                      </strong>
                      <div className="jpg-pdf-order-actions">
                        <button
                          type="button"
                          onClick={() => reorder(index, index - 1)}
                          disabled={running || index === 0}
                          aria-label={`Move ${image.file.name} earlier`}
                        >
                          <ArrowUp size={16} />
                        </button>
                        <button
                          type="button"
                          onClick={() => reorder(index, index + 1)}
                          disabled={running || index === images.length - 1}
                          aria-label={`Move ${image.file.name} later`}
                        >
                          <ArrowDown size={16} />
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ol>
            </div>
          ) : (
            <div className="preview-empty">
              <FileImage size={64} />
              <h3>Upload JPG images</h3>
              <p>Arrange their order, margins, paper size, and orientation.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const validateMargin = (margin) => {
  const value = Number(margin);
  if (!Number.isFinite(value) || value < 0 || value > 144) {
    return 'Margin must be between 0 and 144 points.';
  }
  return null;
};

export default JpgToPdfPage;
