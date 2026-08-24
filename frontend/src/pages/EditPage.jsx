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
  Circle,
  Download,
  Highlighter,
  Image as ImageIcon,
  Minus,
  MousePointer2,
  NotepadText,
  PencilRuler,
  Square,
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
import EditElementPreview from '../features/edit/EditElementPreview';
import EditPropertiesPanel from '../features/edit/EditPropertiesPanel';
import {
  clamp,
  createEditElement,
  MAX_EDIT_IMAGES,
  toEditOperationElement,
  validateEditElements,
} from '../features/edit/editElementModel';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './EditPage.css';

const TOOLS = [
  ['select', 'Select', MousePointer2],
  ['text', 'Text', Type],
  ['image', 'Image', ImageIcon],
  ['rectangle', 'Rectangle', Square],
  ['ellipse', 'Ellipse', Circle],
  ['line', 'Line', Minus],
  ['highlight', 'Highlight', Highlighter],
  ['note', 'Note', NotepadText],
];

const EditPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [images, setImages] = useState([]);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(null);
  const [tool, setTool] = useState('select');
  const [elements, setElements] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [toasts, setToasts] = useState([]);
  const sequenceRef = useRef(0);
  const urlsRef = useRef(new Set());
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

  const selected = elements.find((element) => element.id === selectedId);
  const pageElements = elements.filter(
    (element) => element.page === currentPage,
  );

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Edited PDF download started!', 'success');
    } catch (error) {
      console.error('Edit PDF download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download edited PDF'),
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
        addToast(job.errorMessage || 'Failed to edit PDF', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('Edit PDF job cancelled', 'error');
        return;
      }
      const output = job.outputs[0];
      if (!output) {
        addToast('The edit job completed without an output.', 'error');
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
    await loadPageSize(next, documentRef.current);
  };

  const handlePdfChange = useCallback((files) => {
    if (running) return;
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url));
    urlsRef.current.clear();
    setImages([]);
    setFile(files[0] || null);
    setNumPages(null);
    setCurrentPage(1);
    setPageSize(null);
    setElements([]);
    setSelectedId(null);
    setTool('select');
    documentRef.current = null;
    handledJobRef.current = null;
    reset();
  }, [reset, running]);

  const handleImagesChange = useCallback((files) => {
    if (running) return;
    const accepted = files.slice(0, MAX_EDIT_IMAGES);
    const used = new Set();
    const next = accepted.map((imageFile) => {
      const existing = images.find(
        (item) => item.file === imageFile && !used.has(item.id),
      );
      if (existing) {
        used.add(existing.id);
        return existing;
      }
      const url = URL.createObjectURL(imageFile);
      urlsRef.current.add(url);
      const item = {
        id: `image-${sequenceRef.current}`,
        file: imageFile,
        url,
      };
      sequenceRef.current += 1;
      used.add(item.id);
      return item;
    });
    const removedIds = new Set(
      images
        .filter((item) => !used.has(item.id))
        .map((item) => item.id),
    );
    images
      .filter((item) => removedIds.has(item.id))
      .forEach((item) => {
        URL.revokeObjectURL(item.url);
        urlsRef.current.delete(item.url);
      });
    setImages(next);
    setElements((current) => current.filter(
      (element) => element.type !== 'image'
        || !removedIds.has(element.imageId),
    ));
    if (elements.some(
      (element) => element.id === selectedId
        && element.type === 'image'
        && removedIds.has(element.imageId),
    )) {
      setSelectedId(null);
    }
    if (files.length > MAX_EDIT_IMAGES) {
      addToast(`Use at most ${MAX_EDIT_IMAGES} edit images.`, 'error');
    }
    handledJobRef.current = null;
    reset();
  }, [addToast, elements, images, reset, running, selectedId]);

  const addElement = (event) => {
    if (running || tool === 'select' || !numPages) return;
    if (tool === 'image' && images.length === 0) {
      addToast('Upload an image before placing it.', 'error');
      return;
    }
    const rectangle = event.currentTarget.getBoundingClientRect();
    const x = clamp((event.clientX - rectangle.left) / rectangle.width);
    const y = clamp((event.clientY - rectangle.top) / rectangle.height);
    const element = createEditElement(
      `element-${sequenceRef.current}`,
      tool,
      currentPage,
      x,
      y,
      images[0]?.id,
    );
    sequenceRef.current += 1;
    setElements((current) => [...current, element]);
    setSelectedId(element.id);
    setTool('select');
    handledJobRef.current = null;
    reset();
  };

  const updateSelected = (changes) => {
    setElements((current) => current.map((element) => (
      element.id === selectedId
        ? { ...element, ...changes }
        : element
    )));
    handledJobRef.current = null;
    reset();
  };

  const removeSelected = () => {
    setElements((current) => current.filter(
      (element) => element.id !== selectedId,
    ));
    setSelectedId(null);
    handledJobRef.current = null;
    reset();
  };

  const recordImageDimensions = (imageId, width, height) => {
    setImages((current) => current.map((image) => (
      image.id === imageId
        ? { ...image, width, height }
        : image
    )));
  };

  const handleSubmit = async () => {
    const planError = validateEditElements(elements, images);
    if (!file || !numPages || elements.length === 0 || planError) {
      addToast(
        planError || 'Add at least one edit element.',
        'error',
      );
      return;
    }
    const referencedIds = new Set(
      elements
        .filter((element) => element.type === 'image')
        .map((element) => element.imageId),
    );
    const referencedImages = images.filter(
      (image) => referencedIds.has(image.id),
    );
    const imageIndices = new Map(
      referencedImages.map((image, index) => [image.id, index]),
    );
    try {
      await start(
        'edit',
        [file, ...referencedImages.map((image) => image.file)],
        {
          elements: elements.map((element) => toEditOperationElement(
            element,
            imageIndices,
          )),
        },
      );
    } catch (error) {
      console.error('Edit PDF job failed:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF edit'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Edit PDF cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  const renderWidth = Math.min(620, window.innerWidth - 48);
  const renderHeight = pageSize
    ? renderWidth * pageSize.height / pageSize.width
    : 0;
  const planError = validateEditElements(elements, images);

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <PencilRuler size={28} />
          <h1>Edit PDF</h1>
        </div>
        <p className="operation-description">
          Add text, images, shapes, highlights, and notes in one edit.
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
              <div className="sidebar-section edit-tools">
                <h3 className="sidebar-title">Tools</h3>
                <div className="edit-tool-grid">
                  {TOOLS.map(([value, label, Icon]) => (
                    <button
                      type="button"
                      className={tool === value ? 'active' : ''}
                      onClick={() => setTool(value)}
                      disabled={running}
                      key={value}
                      aria-label={`Add ${label.toLowerCase()}`}
                    >
                      <Icon size={17} />
                      {label}
                    </button>
                  ))}
                </div>
                <p className="edit-help">
                  {tool === 'select'
                    ? 'Choose a tool, then click the page to place it.'
                    : `Click the page to place a ${tool}.`}
                </p>
              </div>

              <div className="sidebar-section">
                <h3 className="sidebar-title">Edit images</h3>
                <FileUpload
                  onFilesChange={handleImagesChange}
                  files={images.map((image) => image.file)}
                  accept={{
                    'image/png': ['.png'],
                    'image/jpeg': ['.jpg', '.jpeg'],
                  }}
                  multiple
                  maxFiles={MAX_EDIT_IMAGES}
                  disabled={running}
                  hint={`Supports up to ${MAX_EDIT_IMAGES} PNG/JPG images`}
                />
              </div>

              {selected && (
                <EditPropertiesPanel
                  selected={selected}
                  images={images}
                  running={running}
                  onChange={updateSelected}
                  onDelete={removeSelected}
                />
              )}

              <div className="sidebar-section edit-summary">
                <h3 className="sidebar-title">Edit plan</h3>
                <p>{elements.length} element(s) across {new Set(
                  elements.map((element) => element.page),
                ).size} page(s)</p>
                {planError && <p className="edit-error">{planError}</p>}
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
                  disabled={running || elements.length === 0 || Boolean(planError)}
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {running ? 'Applying edits...' : 'Apply edits & Download'}
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
              <div className="edit-preview">
                <Document
                  file={file}
                  onLoadSuccess={handleDocumentLoad}
                  loading={<p>Loading PDF editor...</p>}
                >
                  <div
                    className={`edit-preview__page tool-${tool}`}
                    style={{
                      width: renderWidth,
                      height: renderHeight || 'auto',
                    }}
                    onPointerDown={addElement}
                  >
                    <Page
                      pageNumber={currentPage}
                      width={renderWidth}
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                    />
                    {pageElements.map((element) => (
                      <EditElementPreview
                        element={element}
                        images={images}
                        selected={element.id === selectedId}
                        onSelect={setSelectedId}
                        selectMode={tool === 'select'}
                        renderWidth={renderWidth}
                        renderHeight={renderHeight}
                        pageWidth={pageSize?.width || 600}
                        onImageDimensions={recordImageDimensions}
                        key={element.id}
                      />
                    ))}
                  </div>
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <PencilRuler size={64} />
              <h3>Upload a PDF to edit</h3>
              <p>Place text, images, shapes, highlights, and notes.</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default EditPage;
