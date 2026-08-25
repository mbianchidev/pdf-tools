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
  Droplet,
  Image as ImageIcon,
  Type,
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
import './WatermarkPage.css';

const FONTS = [
  ['helvetica', 'Helvetica'],
  ['helvetica-bold', 'Helvetica Bold'],
  ['times', 'Times'],
  ['times-bold', 'Times Bold'],
  ['courier', 'Courier'],
  ['courier-bold', 'Courier Bold'],
];

const FONT_FAMILIES = {
  helvetica: 'Arial, sans-serif',
  'helvetica-bold': 'Arial, sans-serif',
  times: 'Times New Roman, serif',
  'times-bold': 'Times New Roman, serif',
  courier: 'Courier New, monospace',
  'courier-bold': 'Courier New, monospace',
};

const WatermarkPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [image, setImage] = useState(null);
  const [imageUrl, setImageUrl] = useState(null);
  const [imageDimensions, setImageDimensions] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(null);
  const [mode, setMode] = useState('text');
  const [pagesInput, setPagesInput] = useState('all');
  const [opacity, setOpacity] = useState(30);
  const [rotation, setRotation] = useState(45);
  const [position, setPosition] = useState({ x: 0.5, y: 0.5 });
  const [text, setText] = useState('');
  const [font, setFont] = useState('helvetica-bold');
  const [fontSize, setFontSize] = useState(48);
  const [color, setColor] = useState('#4f46e5');
  const [imageWidth, setImageWidth] = useState(35);
  const [toasts, setToasts] = useState([]);
  const documentRef = useRef(null);
  const imageUrlRef = useRef(null);
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

  useEffect(() => () => {
    if (imageUrlRef.current) {
      URL.revokeObjectURL(imageUrlRef.current);
    }
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

  const selection = useMemo(() => {
    if (!numPages) {
      return { pages: [], error: null };
    }
    try {
      return {
        pages: parsePageExpression(pagesInput, numPages, {
          duplicatePolicy: 'reject',
        }),
        error: null,
      };
    } catch (error) {
      return { pages: [], error: error.message };
    }
  }, [numPages, pagesInput]);

  const controlsError = validateControls({
    mode,
    opacity,
    rotation,
    position,
    text,
    fontSize,
    image,
    imageWidth,
  });

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Watermarked PDF download started!', 'success');
    } catch (error) {
      console.error('Watermark download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download watermarked PDF'),
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
        addToast(job.errorMessage || 'Failed to apply watermark', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('Watermark job cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The watermark job completed without an output.', 'error');
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
    await loadPageSize(next, documentRef.current);
  };

  const handlePdfChange = useCallback((files) => {
    if (running) return;
    setFile(files[0] || null);
    setNumPages(null);
    setCurrentPage(1);
    setPageSize(null);
    setPagesInput('all');
    documentRef.current = null;
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handleImageChange = useCallback((files) => {
    if (running) return;
    if (imageUrlRef.current) {
      URL.revokeObjectURL(imageUrlRef.current);
      imageUrlRef.current = null;
    }
    const next = files[0] || null;
    setImage(next);
    setImageDimensions(null);
    if (next) {
      const url = URL.createObjectURL(next);
      imageUrlRef.current = url;
      setImageUrl(url);
    } else {
      setImageUrl(null);
    }
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handlePreviewPosition = (event) => {
    if (running) return;
    const rectangle = event.currentTarget.getBoundingClientRect();
    setPosition({
      x: Math.min(Math.max(
        (event.clientX - rectangle.left) / rectangle.width,
        0,
      ), 1),
      y: Math.min(Math.max(
        (event.clientY - rectangle.top) / rectangle.height,
        0,
      ), 1),
    });
  };

  const handleSubmit = async () => {
    const error = selection.error || controlsError;
    if (!file || !numPages || selection.pages.length === 0 || error) {
      addToast(error || 'Upload a PDF before watermarking it.', 'error');
      return;
    }
    const common = {
      mode,
      pages: pagesInput.trim(),
      opacity: Number(opacity) / 100,
      rotation: Number(rotation),
      x: position.x,
      y: position.y,
    };
    const options = mode === 'text'
      ? {
          ...common,
          text,
          font,
          fontSize: Number(fontSize),
          color,
        }
      : {
          ...common,
          imageWidthPercent: Number(imageWidth),
        };
    try {
      await start(
        'watermark',
        mode === 'text' ? [file] : [file, image],
        options,
      );
    } catch (error) {
      console.error('Watermark job failed:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start watermark job'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Watermark cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const renderWidth = Math.min(560, window.innerWidth - 48);
  const renderHeight = pageSize
    ? renderWidth * pageSize.height / pageSize.width
    : 0;
  const previewScale = pageSize ? renderWidth / pageSize.width : 1;
  const previewImageSize = imageDimensions && renderHeight
    ? fitImagePreview(
        imageDimensions,
        renderWidth,
        renderHeight,
        Number(imageWidth),
      )
    : null;
  const selected = selection.pages.includes(currentPage);
  const canSubmit = Boolean(
    file
    && numPages
    && selection.pages.length > 0
    && !selection.error
    && !controlsError
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
          <Droplet size={28} />
          <h1>Watermark PDF</h1>
        </div>
        <p className="operation-description">
          Apply styled text or image watermarks to selected pages.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload PDF</h3>
            <FileUpload
              onFilesChange={handlePdfChange}
              files={file ? [file] : []}
              multiple={false}
              disabled={running}
            />
          </div>

          {file && (
            <>
              <div className="sidebar-section watermark-controls">
                <h3 className="sidebar-title">Watermark</h3>
                <div className="watermark-mode" role="group" aria-label="Mode">
                  <button
                    type="button"
                    className={mode === 'text' ? 'active' : ''}
                    onClick={() => setMode('text')}
                    disabled={running}
                  >
                    <Type size={16} />
                    Text watermark
                  </button>
                  <button
                    type="button"
                    className={mode === 'image' ? 'active' : ''}
                    onClick={() => setMode('image')}
                    disabled={running}
                  >
                    <ImageIcon size={16} />
                    Image watermark
                  </button>
                </div>

                <label>
                  Pages
                  <input
                    aria-label="Pages"
                    value={pagesInput}
                    onChange={(event) => setPagesInput(event.target.value)}
                    disabled={running}
                  />
                  <span className={selection.error ? 'is-error' : ''}>
                    {selection.error
                      || `${selection.pages.length} page(s) selected`}
                  </span>
                </label>

                {mode === 'text' ? (
                  <>
                    <label>
                      Watermark text
                      <input
                        aria-label="Watermark text"
                        value={text}
                        maxLength={100}
                        onChange={(event) => setText(event.target.value)}
                        disabled={running}
                      />
                    </label>
                    <div className="watermark-two-columns">
                      <label>
                        Font
                        <select
                          value={font}
                          onChange={(event) => setFont(event.target.value)}
                          disabled={running}
                        >
                          {FONTS.map(([value, label]) => (
                            <option value={value} key={value}>{label}</option>
                          ))}
                        </select>
                      </label>
                      <label>
                        Font size
                        <input
                          aria-label="Font size"
                          type="number"
                          min="8"
                          max="144"
                          value={fontSize}
                          onChange={(event) => setFontSize(event.target.value)}
                          disabled={running}
                        />
                      </label>
                    </div>
                    <label>
                      Color
                      <input
                        aria-label="Color"
                        type="color"
                        value={color}
                        onChange={(event) => setColor(event.target.value)}
                        disabled={running}
                      />
                    </label>
                  </>
                ) : (
                  <>
                    <FileUpload
                      onFilesChange={handleImageChange}
                      files={image ? [image] : []}
                      accept={{
                        'image/png': ['.png'],
                        'image/jpeg': ['.jpg', '.jpeg'],
                      }}
                      multiple={false}
                      disabled={running}
                      hint="Supports one PNG or JPG image"
                    />
                    <label>
                      Image width
                      <input
                        aria-label="Image width"
                        type="number"
                        min="5"
                        max="100"
                        value={imageWidth}
                        onChange={(event) => setImageWidth(event.target.value)}
                        disabled={running}
                      />
                      <span>Percent of page width</span>
                    </label>
                  </>
                )}

                <div className="watermark-two-columns">
                  <label>
                    Opacity
                    <input
                      aria-label="Opacity"
                      type="number"
                      min="5"
                      max="100"
                      value={opacity}
                      onChange={(event) => setOpacity(event.target.value)}
                      disabled={running}
                    />
                  </label>
                  <label>
                    Rotation
                    <input
                      aria-label="Rotation"
                      type="number"
                      min="-180"
                      max="180"
                      value={rotation}
                      onChange={(event) => setRotation(event.target.value)}
                      disabled={running}
                    />
                  </label>
                  <label>
                    X position
                    <input
                      aria-label="X position"
                      type="number"
                      min="0"
                      max="100"
                      value={Math.round(position.x * 100)}
                      onChange={(event) => setPosition((current) => ({
                        ...current,
                        x: Number(event.target.value) / 100,
                      }))}
                      disabled={running}
                    />
                  </label>
                  <label>
                    Y position
                    <input
                      aria-label="Y position"
                      type="number"
                      min="0"
                      max="100"
                      value={Math.round(position.y * 100)}
                      onChange={(event) => setPosition((current) => ({
                        ...current,
                        y: Number(event.target.value) / 100,
                      }))}
                      disabled={running}
                    />
                  </label>
                </div>
                <p className={controlsError
                  ? 'watermark-error'
                  : 'watermark-help'}>
                  {controlsError
                    || 'Click the preview to position the watermark.'}
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
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Applying watermark...' : 'Apply & Download'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <span>
                  Page {currentPage} of {numPages || '?'}
                  {' · '}
                  {selected ? 'Selected' : 'Not selected'}
                </span>
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
              <div className="watermark-preview">
                <Document
                  file={file}
                  onLoadSuccess={handleDocumentLoad}
                  loading={<p>Loading PDF preview...</p>}
                >
                  <div
                    className="watermark-preview__page"
                    style={{
                      width: renderWidth,
                      height: renderHeight || 'auto',
                    }}
                    onPointerDown={handlePreviewPosition}
                  >
                    <Page
                      pageNumber={currentPage}
                      width={renderWidth}
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                    />
                    {selected && mode === 'text' && text && (
                      <span
                        className="watermark-preview__text"
                        style={{
                          left: `${position.x * 100}%`,
                          top: `${position.y * 100}%`,
                          color,
                          opacity: Number(opacity) / 100,
                          fontFamily: FONT_FAMILIES[font],
                          fontWeight: font.includes('bold') ? 700 : 400,
                          fontSize: `${Number(fontSize) * previewScale}px`,
                          transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
                        }}
                      >
                        {text}
                      </span>
                    )}
                    {selected && mode === 'image' && imageUrl && (
                      <img
                        className="watermark-preview__image"
                        src={imageUrl}
                        alt=""
                        onLoad={(event) => setImageDimensions({
                          width: event.currentTarget.naturalWidth,
                          height: event.currentTarget.naturalHeight,
                        })}
                        style={{
                          left: `${position.x * 100}%`,
                          top: `${position.y * 100}%`,
                          width: previewImageSize
                            ? `${previewImageSize.width}px`
                            : `${imageWidth}%`,
                          height: previewImageSize
                            ? `${previewImageSize.height}px`
                            : 'auto',
                          opacity: Number(opacity) / 100,
                          transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
                        }}
                      />
                    )}
                  </div>
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Droplet size={64} />
              <h3>Upload a PDF to watermark</h3>
              <p>Choose text or image mode and select target pages.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

const validateControls = ({
  mode,
  opacity,
  rotation,
  position,
  text,
  fontSize,
  image,
  imageWidth,
}) => {
  if (Number(opacity) < 5 || Number(opacity) > 100) {
    return 'Opacity must be between 5 and 100 percent.';
  }
  if (Number(rotation) < -180 || Number(rotation) > 180) {
    return 'Rotation must be between -180 and 180 degrees.';
  }
  if (
    position.x < 0
    || position.x > 1
    || position.y < 0
    || position.y > 1
  ) {
    return 'Position must stay within the page.';
  }
  if (mode === 'text') {
    if (!text.trim()) return 'Enter watermark text.';
    if (!/^[\x20-\x7e]+$/.test(text)) {
      return 'Watermark text must use printable ASCII characters.';
    }
    if (Number(fontSize) < 8 || Number(fontSize) > 144) {
      return 'Font size must be between 8 and 144 points.';
    }
  } else {
    if (!image) return 'Upload a PNG or JPG watermark image.';
    if (Number(imageWidth) < 5 || Number(imageWidth) > 100) {
      return 'Image width must be between 5 and 100 percent.';
    }
  }
  return null;
};

const fitImagePreview = (
  image,
  pageWidth,
  pageHeight,
  widthPercent,
) => {
  const maxWidth = pageWidth * widthPercent / 100;
  const maxHeight = pageHeight * 0.9;
  const scale = Math.min(
    maxWidth / image.width,
    maxHeight / image.height,
  );
  return {
    width: image.width * scale,
    height: image.height * scale,
  };
};

export default WatermarkPage;
