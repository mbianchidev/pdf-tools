import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { PenTool, ArrowLeft, Download, Type, Upload, Eraser, Save } from 'lucide-react';
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
import './SignaturePage.css';

const SignaturePage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDimensions, setPageDimensions] = useState({ width: 595, height: 842 });
  const [scale, setScale] = useState(1);
  
  // Signature modes: 'draw', 'type', 'upload'
  const [signatureMode, setSignatureMode] = useState('type');
  const [signatureBlob, setSignatureBlob] = useState(null);
  const [signaturePreview, setSignaturePreview] = useState(null);
  
  // Type mode
  const [typedName, setTypedName] = useState('');
  const [fontStyle, setFontStyle] = useState('cursive');
  
  // Draw mode
  const [isDrawing, setIsDrawing] = useState(false);
  const canvasRef = useRef(null);
  
  // Position
  const [signaturePosition, setSignaturePosition] = useState({ x: 400, y: 100 });
  const [signatureDimensions, setSignatureDimensions] = useState({ width: 150, height: 50 });
  const [pageNum, setPageNum] = useState(1);
  const [dragging, setDragging] = useState(false);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  
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
      if (signaturePreview) URL.revokeObjectURL(signaturePreview);
    };
  }, [fileUrl, signaturePreview]);

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

  // Generate signature from typed name
  const generateTypedSignature = useCallback(() => {
    if (!typedName.trim()) return;
    
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    
    let fontFamily = 'cursive';
    if (fontStyle === 'elegant') fontFamily = 'Georgia, serif';
    if (fontStyle === 'modern') fontFamily = 'Arial, sans-serif';
    if (fontStyle === 'handwriting') fontFamily = "'Brush Script MT', cursive";
    
    ctx.font = `italic 48px ${fontFamily}`;
    const metrics = ctx.measureText(typedName);
    const textWidth = metrics.width + 20;
    const textHeight = 60;
    
    canvas.width = textWidth;
    canvas.height = textHeight;
    
    ctx.font = `italic 48px ${fontFamily}`;
    ctx.fillStyle = '#000000';
    ctx.textBaseline = 'middle';
    ctx.fillText(typedName, 10, textHeight / 2);
    
    canvas.toBlob((blob) => {
      setSignatureBlob(blob);
      setSignaturePreview(URL.createObjectURL(blob));
      setSignatureDimensions({ width: textWidth * 0.5, height: textHeight * 0.5 });
    }, 'image/png');
  }, [typedName, fontStyle]);

  useEffect(() => {
    if (signatureMode === 'type' && typedName.trim()) {
      generateTypedSignature();
    }
  }, [typedName, fontStyle, signatureMode, generateTypedSignature]);

  // Drawing canvas setup
  useEffect(() => {
    if (signatureMode === 'draw' && canvasRef.current) {
      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      ctx.strokeStyle = '#000000';
      ctx.lineWidth = 2;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
    }
  }, [signatureMode]);

  const startDrawing = (e) => {
    if (!canvasRef.current) return;
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const ctx = canvas.getContext('2d');
    ctx.beginPath();
    ctx.moveTo(e.clientX - rect.left, e.clientY - rect.top);
    setIsDrawing(true);
  };

  const draw = (e) => {
    if (!isDrawing || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const ctx = canvas.getContext('2d');
    ctx.lineTo(e.clientX - rect.left, e.clientY - rect.top);
    ctx.stroke();
  };

  const stopDrawing = () => {
    setIsDrawing(false);
  };

  const clearCanvas = () => {
    if (!canvasRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setSignatureBlob(null);
    setSignaturePreview(null);
  };

  const saveDrawnSignature = () => {
    if (!canvasRef.current) return;
    canvasRef.current.toBlob((blob) => {
      setSignatureBlob(blob);
      setSignaturePreview(URL.createObjectURL(blob));
      addToast('Signature saved!', 'success');
    }, 'image/png');
  };

  // Handle signature image upload
  const handleSignatureUpload = useCallback((files) => {
    const imgFile = files[0];
    if (!imgFile) return;
    setSignatureBlob(imgFile);
    setSignaturePreview(URL.createObjectURL(imgFile));
    
    // Get image dimensions
    const img = new Image();
    img.onload = () => {
      const maxWidth = 200;
      const ratio = img.width / img.height;
      const width = Math.min(img.width, maxWidth);
      const height = width / ratio;
      setSignatureDimensions({ width, height });
    };
    img.src = URL.createObjectURL(imgFile);
  }, []);

  // Dragging signature on PDF
  const handleMouseDown = (e) => {
    e.preventDefault();
    setDragging(true);
    const rect = e.target.getBoundingClientRect();
    setDragOffset({ x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  const handleMouseMove = useCallback((e) => {
    if (!dragging || !previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left - dragOffset.x) / scale;
    const y = pageDimensions.height - ((e.clientY - rect.top - dragOffset.y + signatureDimensions.height * scale) / scale);
    setSignaturePosition({
      x: Math.max(0, Math.min(x, pageDimensions.width - signatureDimensions.width)),
      y: Math.max(0, Math.min(y, pageDimensions.height - signatureDimensions.height)),
    });
  }, [dragging, scale, pageDimensions, dragOffset, signatureDimensions]);

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

  // Download signature as PNG
  const downloadSignature = () => {
    if (!signatureBlob) {
      addToast('No signature to download', 'error');
      return;
    }
    const link = document.createElement('a');
    link.href = signaturePreview;
    link.download = 'signature.png';
    link.click();
    addToast('Signature downloaded as PNG!', 'success');
  };

  // Apply signature to PDF
  const handleAddSignature = async () => {
    if (!file) { addToast('Please upload a PDF file', 'error'); return; }
    if (!signatureBlob) { addToast('Please create or upload a signature', 'error'); return; }

    setLoading(true);
    try {
      const result = await pdfService.addSignature(
        file, signatureBlob,
        signaturePosition.x, signaturePosition.y, pageNum
      );
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_signed.pdf`);
      addToast('Signature added successfully!', 'success');
    } catch (error) {
      console.error('Signature error:', error);
      addToast(error.response?.data?.message || error.message || 'Failed to add signature', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} /><span>Back</span>
        </button>
        <div className="operation-title">
          <PenTool size={28} /><h1>Add Signature</h1>
        </div>
        <p className="operation-description">Create a signature by typing, drawing, or uploading. Drag to position it on the PDF.</p>
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
                <h3 className="sidebar-title">Create Signature</h3>
                <div className="signature-mode-tabs">
                  <button className={`mode-tab ${signatureMode === 'type' ? 'active' : ''}`} onClick={() => setSignatureMode('type')}>
                    <Type size={16} />Type
                  </button>
                  <button className={`mode-tab ${signatureMode === 'draw' ? 'active' : ''}`} onClick={() => setSignatureMode('draw')}>
                    <PenTool size={16} />Draw
                  </button>
                  <button className={`mode-tab ${signatureMode === 'upload' ? 'active' : ''}`} onClick={() => setSignatureMode('upload')}>
                    <Upload size={16} />Upload
                  </button>
                </div>

                {signatureMode === 'type' && (
                  <div className="type-signature">
                    <Input label="Your Name" placeholder="Type your name" value={typedName} onChange={(e) => setTypedName(e.target.value)} />
                    <div className="input-group">
                      <label className="input-label">Font Style</label>
                      <select className="font-select" value={fontStyle} onChange={(e) => setFontStyle(e.target.value)}>
                        <option value="cursive">Cursive</option>
                        <option value="elegant">Elegant</option>
                        <option value="modern">Modern</option>
                        <option value="handwriting">Handwriting</option>
                      </select>
                    </div>
                  </div>
                )}

                {signatureMode === 'draw' && (
                  <div className="draw-signature">
                    <canvas ref={canvasRef} width={280} height={100} className="signature-canvas"
                      onMouseDown={startDrawing} onMouseMove={draw} onMouseUp={stopDrawing} onMouseLeave={stopDrawing} />
                    <div className="canvas-actions">
                      <button className="canvas-btn" onClick={clearCanvas}><Eraser size={16} />Clear</button>
                      <button className="canvas-btn primary" onClick={saveDrawnSignature}><Save size={16} />Save</button>
                    </div>
                  </div>
                )}

                {signatureMode === 'upload' && (
                  <div className="upload-signature">
                    <FileUpload onFilesChange={handleSignatureUpload} files={signatureBlob && signatureMode === 'upload' ? [signatureBlob] : []} 
                      accept={{ 'image/*': ['.png', '.jpg', '.jpeg'] }} multiple={false} />
                  </div>
                )}

                {signaturePreview && (
                  <div className="signature-preview-box">
                    <label className="input-label">Preview</label>
                    <img src={signaturePreview} alt="Signature preview" className="sig-preview-img" />
                    <button className="download-sig-btn" onClick={downloadSignature}><Download size={14} />Save as PNG</button>
                  </div>
                )}
              </div>

              {signatureBlob && (
                <div className="sidebar-section">
                  <h3 className="sidebar-title">Position</h3>
                  <p className="position-hint">Drag signature on preview or set coordinates:</p>
                  <div className="input-group">
                    <label className="input-label">Page</label>
                    <select className="page-select" value={pageNum} onChange={(e) => { setPageNum(parseInt(e.target.value)); setCurrentPage(parseInt(e.target.value)); }}>
                      {Array.from({ length: numPages || 1 }, (_, i) => <option key={i + 1} value={i + 1}>Page {i + 1}</option>)}
                    </select>
                  </div>
                  <div className="input-row">
                    <Input label="X" type="number" value={Math.round(signaturePosition.x)} onChange={(e) => setSignaturePosition(p => ({ ...p, x: parseFloat(e.target.value) || 0 }))} />
                    <Input label="Y" type="number" value={Math.round(signaturePosition.y)} onChange={(e) => setSignaturePosition(p => ({ ...p, y: parseFloat(e.target.value) || 0 }))} />
                  </div>
                </div>
              )}
            </>
          )}

          {file && signatureBlob && (
            <div className="sidebar-actions">
              <Button onClick={handleAddSignature} loading={loading} disabled={loading} icon={<Download size={20} />} fullWidth size="lg">
                {loading ? 'Adding Signature...' : 'Apply & Download'}
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
                  {signaturePreview && currentPage === pageNum && (
                    <div className="signature-overlay" style={{
                      left: signaturePosition.x * scale,
                      bottom: signaturePosition.y * scale,
                      cursor: 'move',
                    }} onMouseDown={handleMouseDown}>
                      <img src={signaturePreview} alt="Signature" style={{ width: signatureDimensions.width * scale, height: signatureDimensions.height * scale }} />
                    </div>
                  )}
                </div>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <PenTool size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Create and position your signature</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default SignaturePage;
