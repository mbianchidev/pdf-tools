import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { EyeOff, ArrowLeft, Download, Plus, Trash2 } from 'lucide-react';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

// Set worker source for PDF.js
pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';
import './RedactPage.css';

const RedactPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDimensions, setPageDimensions] = useState({ width: 595, height: 842 });
  const [scale, setScale] = useState(1);
  
  // Redaction areas
  const [redactions, setRedactions] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  
  // Drawing state
  const [isDrawing, setIsDrawing] = useState(false);
  const [drawStart, setDrawStart] = useState({ x: 0, y: 0 });
  const [currentRect, setCurrentRect] = useState(null);
  
  const [loading, setLoading] = useState(false);
  const [toasts, setToasts] = useState([]);
  
  const previewRef = useRef(null);
  const containerRef = useRef(null);

  const addToast = (message, type = 'success', duration = 5000) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const handleFilesChange = useCallback((files) => {
    const newFile = files[0] || null;
    setFile(newFile);
    setRedactions([]);
    setSelectedId(null);
    if (newFile) {
      setFileUrl(URL.createObjectURL(newFile));
    } else {
      setFileUrl(null);
    }
  }, []);

  useEffect(() => {
    return () => {
      if (fileUrl) URL.revokeObjectURL(fileUrl);
    };
  }, [fileUrl]);

  const onDocumentLoadSuccess = ({ numPages }) => {
    setNumPages(numPages);
    setCurrentPage(1);
  };

  const onPageLoadSuccess = (page) => {
    const { width, height } = page;
    setPageDimensions({ width, height });
    if (containerRef.current) {
      const containerWidth = containerRef.current.clientWidth - 48;
      setScale(Math.min(containerWidth / width, 1));
    }
  };

  const removeRedaction = (id) => {
    setRedactions(redactions.filter(r => r.id !== id));
    if (selectedId === id) setSelectedId(null);
  };

  // Drawing redaction boxes
  const handleMouseDown = (e) => {
    if (!previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scale;
    const y = (e.clientY - rect.top) / scale;
    setDrawStart({ x, y });
    setIsDrawing(true);
    setCurrentRect({ x, y, width: 0, height: 0 });
  };

  const handleMouseMove = useCallback((e) => {
    if (!isDrawing || !previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scale;
    const y = (e.clientY - rect.top) / scale;
    
    const newRect = {
      x: Math.min(drawStart.x, x),
      y: Math.min(drawStart.y, y),
      width: Math.abs(x - drawStart.x),
      height: Math.abs(y - drawStart.y),
    };
    setCurrentRect(newRect);
  }, [isDrawing, drawStart, scale]);

  const handleMouseUp = useCallback(() => {
    if (!isDrawing || !currentRect) return;
    
    // Only add if rectangle has meaningful size
    if (currentRect.width > 10 && currentRect.height > 5) {
      // Convert to PDF coordinates (y is from bottom)
      const pdfY = pageDimensions.height - currentRect.y - currentRect.height;
      
      const newRedaction = {
        id: Date.now(),
        pageNum: currentPage,
        x: currentRect.x,
        y: pdfY,
        width: currentRect.width,
        height: currentRect.height,
        // Store display coordinates too
        displayX: currentRect.x,
        displayY: currentRect.y,
      };
      setRedactions([...redactions, newRedaction]);
      setSelectedId(newRedaction.id);
    }
    
    setIsDrawing(false);
    setCurrentRect(null);
  }, [isDrawing, currentRect, currentPage, pageDimensions, redactions]);

  useEffect(() => {
    if (isDrawing) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
      return () => {
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [isDrawing, handleMouseMove, handleMouseUp]);

  const handleRedact = async () => {
    if (!file) { addToast('Please upload a PDF file', 'error'); return; }
    if (redactions.length === 0) { addToast('Please draw at least one redaction area', 'error'); return; }

    setLoading(true);
    try {
      // Format redactions for backend
      const formattedRedactions = redactions.map(r => ({
        x: r.x,
        y: r.y,
        width: r.width,
        height: r.height,
        pageNum: r.pageNum,
      }));
      
      const result = await pdfService.redactMultiple(file, formattedRedactions);
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_redacted.pdf`);
      addToast('Content redacted successfully!', 'success');
    } catch (error) {
      console.error('Redact error:', error);
      addToast(error.response?.data?.message || error.message || 'Failed to redact content', 'error');
    } finally {
      setLoading(false);
    }
  };

  const currentPageRedactions = redactions.filter(r => r.pageNum === currentPage);

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} /><span>Back</span>
        </button>
        <div className="operation-title">
          <EyeOff size={28} /><h1>Redact Content</h1>
        </div>
        <p className="operation-description">Draw rectangles to cover sensitive content. Click and drag on the PDF preview.</p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload PDF</h3>
            <FileUpload onFilesChange={handleFilesChange} files={file ? [file] : []} multiple={false} />
          </div>

          {file && (
            <div className="sidebar-section">
              <div className="section-header">
                <h3 className="sidebar-title">Redaction Areas</h3>
                <span className="redaction-count">{redactions.length} area{redactions.length !== 1 ? 's' : ''}</span>
              </div>
              
              <p className="draw-hint">Click and drag on the preview to draw redaction boxes</p>
              
              <div className="redactions-list">
                {redactions.length === 0 ? (
                  <p className="no-items-hint">No redaction areas yet</p>
                ) : (
                  redactions.map((r, index) => (
                    <div key={r.id} className={`redaction-item ${selectedId === r.id ? 'selected' : ''}`}
                      onClick={() => { setSelectedId(r.id); setCurrentPage(r.pageNum); }}>
                      <div className="redaction-info">
                        <span className="redaction-label">Area {index + 1}</span>
                        <span className="redaction-dims">{Math.round(r.width)}×{Math.round(r.height)}</span>
                      </div>
                      <span className="redaction-page">Page {r.pageNum}</span>
                      <button className="redaction-delete" onClick={(e) => { e.stopPropagation(); removeRedaction(r.id); }}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {file && redactions.length > 0 && (
            <div className="sidebar-actions">
              <Button onClick={handleRedact} loading={loading} disabled={loading} icon={<Download size={20} />} fullWidth size="lg">
                {loading ? 'Redacting...' : 'Redact & Download'}
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview" ref={containerRef}>
          {file && fileUrl ? (
            <>
              <div className="preview-header">
                <h3>Preview: {file.name}</h3>
                <div className="page-nav">
                  <button disabled={currentPage <= 1} onClick={() => setCurrentPage(p => p - 1)}>←</button>
                  <span>Page {currentPage} of {numPages || '?'}</span>
                  <button disabled={currentPage >= (numPages || 1)} onClick={() => setCurrentPage(p => p + 1)}>→</button>
                </div>
              </div>
              <div className="pdf-preview-container">
                <div className="pdf-page-wrapper redact-mode" ref={previewRef} 
                  style={{ position: 'relative', width: pageDimensions.width * scale, height: pageDimensions.height * scale, cursor: 'crosshair' }}
                  onMouseDown={handleMouseDown}>
                  <Document file={fileUrl} onLoadSuccess={onDocumentLoadSuccess} loading={<div className="loading-placeholder">Loading PDF...</div>}>
                    <Page pageNumber={currentPage} scale={scale} onLoadSuccess={onPageLoadSuccess} renderTextLayer={false} renderAnnotationLayer={false} />
                  </Document>
                  
                  {/* Existing redactions */}
                  {currentPageRedactions.map((r) => (
                    <div key={r.id} className={`redact-overlay ${selectedId === r.id ? 'selected' : ''}`}
                      style={{
                        left: r.displayX * scale,
                        top: r.displayY * scale,
                        width: r.width * scale,
                        height: r.height * scale,
                      }}
                      onClick={(e) => { e.stopPropagation(); setSelectedId(r.id); }}>
                    </div>
                  ))}
                  
                  {/* Currently drawing rectangle */}
                  {isDrawing && currentRect && (
                    <div className="redact-overlay drawing" style={{
                      left: currentRect.x * scale,
                      top: currentRect.y * scale,
                      width: currentRect.width * scale,
                      height: currentRect.height * scale,
                    }} />
                  )}
                </div>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <EyeOff size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Draw boxes to black out sensitive information</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default RedactPage;
