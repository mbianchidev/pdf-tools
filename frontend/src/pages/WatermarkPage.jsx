import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Droplet, ArrowLeft, Download } from 'lucide-react';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

// Set worker source for PDF.js
pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';
import './WatermarkPage.css';

const WatermarkPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDimensions, setPageDimensions] = useState({ width: 595, height: 842 });
  const [scale, setScale] = useState(1);
  
  const [watermarkText, setWatermarkText] = useState('');
  const [position, setPosition] = useState({ x: null, y: null }); // null = center
  const [rotation, setRotation] = useState(45);
  const [opacity, setOpacity] = useState(0.3);
  
  const [dragging, setDragging] = useState(false);
  
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
    // Initialize position to center
    setPosition({ x: width / 2, y: height / 2 });
  };

  const handleTextChange = (e) => {
    const value = e.target.value;
    if (value.length <= 30) {
      setWatermarkText(value);
    }
  };

  // Dragging watermark
  const handleMouseDown = (e) => {
    e.preventDefault();
    setDragging(true);
  };

  const handleMouseMove = useCallback((e) => {
    if (!dragging || !previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scale;
    const y = pageDimensions.height - ((e.clientY - rect.top) / scale);
    setPosition({
      x: Math.max(50, Math.min(x, pageDimensions.width - 50)),
      y: Math.max(50, Math.min(y, pageDimensions.height - 50)),
    });
  }, [dragging, scale, pageDimensions]);

  const handleMouseUp = useCallback(() => setDragging(false), []);

  useEffect(() => {
    if (dragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
      return () => {
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [dragging, handleMouseMove, handleMouseUp]);

  const handleWatermark = async () => {
    if (!file) { addToast('Please upload a PDF file', 'error'); return; }
    if (!watermarkText.trim()) { addToast('Please enter watermark text', 'error'); return; }

    setLoading(true);
    try {
      const result = await pdfService.addWatermark(
        file, watermarkText, position.x, position.y, rotation, opacity
      );
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_watermarked.pdf`);
      addToast('Watermark added successfully!', 'success');
    } catch (error) {
      console.error('Watermark error:', error);
      addToast(error.response?.data?.message || error.message || 'Failed to add watermark', 'error');
    } finally {
      setLoading(false);
    }
  };

  // Calculate watermark display position
  const displayX = (position.x || pageDimensions.width / 2) * scale;
  const displayY = pageDimensions.height * scale - (position.y || pageDimensions.height / 2) * scale;

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} /><span>Back</span>
        </button>
        <div className="operation-title">
          <Droplet size={28} /><h1>Add Watermark</h1>
        </div>
        <p className="operation-description">Add a text watermark to all pages. Drag to position it.</p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload PDF</h3>
            <FileUpload onFilesChange={handleFilesChange} files={file ? [file] : []} multiple={false} />
          </div>

          {file && (
            <>
              <div className="sidebar-section">
                <h3 className="sidebar-title">Watermark Settings</h3>
                <div className="input-with-counter">
                  <Input label="Watermark Text" placeholder="e.g., CONFIDENTIAL" value={watermarkText} onChange={handleTextChange} />
                  <span className="char-counter">{watermarkText.length}/30</span>
                </div>
                
                <div className="input-group">
                  <label className="input-label">Rotation ({rotation}°)</label>
                  <input type="range" min="0" max="90" value={rotation} onChange={(e) => setRotation(parseInt(e.target.value))} className="range-slider" />
                </div>
                
                <div className="input-group">
                  <label className="input-label">Opacity ({Math.round(opacity * 100)}%)</label>
                  <input type="range" min="10" max="80" value={opacity * 100} onChange={(e) => setOpacity(parseInt(e.target.value) / 100)} className="range-slider" />
                </div>

                <p className="position-hint">Drag watermark on preview to position:</p>
                <div className="input-row">
                  <Input label="X" type="number" value={Math.round(position.x || 0)} onChange={(e) => setPosition(p => ({ ...p, x: parseFloat(e.target.value) || 0 }))} />
                  <Input label="Y" type="number" value={Math.round(position.y || 0)} onChange={(e) => setPosition(p => ({ ...p, y: parseFloat(e.target.value) || 0 }))} />
                </div>
              </div>
            </>
          )}

          {file && (
            <div className="sidebar-actions">
              <Button onClick={handleWatermark} loading={loading} disabled={loading || !watermarkText.trim()} icon={<Download size={20} />} fullWidth size="lg">
                {loading ? 'Adding Watermark...' : 'Add Watermark & Download'}
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
                <div className="pdf-page-wrapper" ref={previewRef} style={{ position: 'relative', width: pageDimensions.width * scale, height: pageDimensions.height * scale }}>
                  <Document file={fileUrl} onLoadSuccess={onDocumentLoadSuccess} loading={<div className="loading-placeholder">Loading PDF...</div>}>
                    <Page pageNumber={currentPage} scale={scale} onLoadSuccess={onPageLoadSuccess} renderTextLayer={false} renderAnnotationLayer={false} />
                  </Document>
                  {watermarkText && (
                    <div className="watermark-overlay" style={{
                      left: displayX,
                      top: displayY,
                      transform: `translate(-50%, -50%) rotate(-${rotation}deg)`,
                      opacity: opacity,
                      cursor: 'move',
                    }} onMouseDown={handleMouseDown}>
                      {watermarkText}
                    </div>
                  )}
                </div>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Droplet size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Add a watermark text across all pages</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default WatermarkPage;
