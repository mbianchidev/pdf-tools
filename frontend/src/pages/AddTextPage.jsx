import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Type, ArrowLeft, Download, Plus, Trash2, GripVertical } from 'lucide-react';
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
import './AddTextPage.css';

const FONTS = [
  { value: 'HELVETICA', label: 'Helvetica' },
  { value: 'HELVETICA_BOLD', label: 'Helvetica Bold' },
  { value: 'HELVETICA_OBLIQUE', label: 'Helvetica Italic' },
  { value: 'TIMES_ROMAN', label: 'Times Roman' },
  { value: 'TIMES_BOLD', label: 'Times Bold' },
  { value: 'TIMES_ITALIC', label: 'Times Italic' },
  { value: 'COURIER', label: 'Courier' },
  { value: 'COURIER_BOLD', label: 'Courier Bold' },
];

const FONT_SIZES = [8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72];

const AddTextPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDimensions, setPageDimensions] = useState({ width: 595, height: 842 });
  const [scale, setScale] = useState(1);
  
  const [textItems, setTextItems] = useState([]);
  const [selectedItemId, setSelectedItemId] = useState(null);
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
    setTextItems([]);
    setSelectedItemId(null);
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

  const addTextItem = () => {
    const newItem = {
      id: Date.now(),
      text: 'New Text',
      x: 50,
      y: pageDimensions.height - 100,
      pageNum: currentPage,
      fontSize: 14,
      fontName: 'HELVETICA',
      fontColor: '#000000',
    };
    setTextItems(prevItems => [...prevItems, newItem]);
    setSelectedItemId(newItem.id);
  };

  const updateTextItem = useCallback((id, updates) => {
    setTextItems(prevItems => prevItems.map(item => item.id === id ? { ...item, ...updates } : item));
  }, []);

  const removeTextItem = (id) => {
    setTextItems(prevItems => prevItems.filter(item => item.id !== id));
    if (selectedItemId === id) setSelectedItemId(null);
  };

  const handleMouseDown = (e, item) => {
    e.preventDefault();
    setSelectedItemId(item.id);
    setDragging(true);
    const rect = e.target.getBoundingClientRect();
    setDragOffset({ x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  const handleMouseMove = useCallback((e) => {
    if (!dragging || !selectedItemId || !previewRef.current) return;
    const rect = previewRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left - dragOffset.x) / scale;
    const y = pageDimensions.height - ((e.clientY - rect.top - dragOffset.y) / scale);
    updateTextItem(selectedItemId, {
      x: Math.max(0, Math.min(x, pageDimensions.width - 50)),
      y: Math.max(0, Math.min(y, pageDimensions.height - 20)),
    });
  }, [dragging, selectedItemId, scale, pageDimensions, dragOffset, updateTextItem]);

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

  const handleAddAllText = async () => {
    if (!file) { addToast('Please upload a PDF file', 'error'); return; }
    if (textItems.length === 0) { addToast('Please add at least one text item', 'error'); return; }

    setLoading(true);
    try {
      let currentBlob = file;
      for (const item of textItems) {
        currentBlob = await pdfService.addText(
          currentBlob, item.text, item.x, item.y, item.pageNum,
          item.fontSize, item.fontName, item.fontColor
        );
      }
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(currentBlob, `${baseName}_text_added.pdf`);
      addToast('Text added successfully!', 'success');
    } catch (error) {
      console.error('Add text error:', error);
      addToast(error.response?.data?.message || error.message || 'Failed to add text', 'error');
    } finally {
      setLoading(false);
    }
  };

  const selectedItem = textItems.find(item => item.id === selectedItemId);
  const currentPageItems = textItems.filter(item => item.pageNum === currentPage);

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} /><span>Back</span>
        </button>
        <div className="operation-title">
          <Type size={28} /><h1>Add/Edit Text</h1>
        </div>
        <p className="operation-description">Add text annotations to your PDF. Drag text on the preview to position it.</p>
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
                <div className="section-header">
                  <h3 className="sidebar-title">Text Items</h3>
                  <button className="add-text-btn" onClick={addTextItem}>
                    <Plus size={16} />Add Text
                  </button>
                </div>
                <div className="text-items-list">
                  {textItems.length === 0 ? (
                    <p className="no-items-hint">Click "Add Text" to create text annotations</p>
                  ) : textItems.map((item, index) => (
                    <div key={item.id} className={`text-item ${selectedItemId === item.id ? 'selected' : ''}`} onClick={() => setSelectedItemId(item.id)}>
                      <GripVertical size={14} className="grip-icon" />
                      <div className="text-item-info">
                        <span className="text-item-label">Text {index + 1}</span>
                        <span className="text-item-preview">{item.text.substring(0, 20)}{item.text.length > 20 ? '...' : ''}</span>
                      </div>
                      <span className="text-item-page">Page {item.pageNum}</span>
                      <button className="text-item-delete" onClick={(e) => { e.stopPropagation(); removeTextItem(item.id); }}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              {selectedItem && (
                <div className="sidebar-section">
                  <h3 className="sidebar-title">Edit Selected Text</h3>
                  <Input label="Text Content" placeholder="Enter text" value={selectedItem.text} onChange={(e) => updateTextItem(selectedItemId, { text: e.target.value })} />
                  <div className="input-group">
                    <label className="input-label">Font</label>
                    <select className="font-select" value={selectedItem.fontName} onChange={(e) => updateTextItem(selectedItemId, { fontName: e.target.value })}>
                      {FONTS.map(font => <option key={font.value} value={font.value}>{font.label}</option>)}
                    </select>
                  </div>
                  <div className="input-row">
                    <div className="input-group">
                      <label className="input-label">Size</label>
                      <select className="size-select" value={selectedItem.fontSize} onChange={(e) => updateTextItem(selectedItemId, { fontSize: parseInt(e.target.value) })}>
                        {FONT_SIZES.map(size => <option key={size} value={size}>{size}pt</option>)}
                      </select>
                    </div>
                    <div className="input-group">
                      <label className="input-label">Color</label>
                      <input type="color" className="color-picker" value={selectedItem.fontColor} onChange={(e) => updateTextItem(selectedItemId, { fontColor: e.target.value })} />
                    </div>
                  </div>
                  <div className="input-group">
                    <label className="input-label">Page</label>
                    <select className="page-select" value={selectedItem.pageNum} onChange={(e) => { const p = parseInt(e.target.value); updateTextItem(selectedItemId, { pageNum: p }); setCurrentPage(p); }}>
                      {Array.from({ length: numPages || 1 }, (_, i) => <option key={i + 1} value={i + 1}>Page {i + 1}</option>)}
                    </select>
                  </div>
                  <p className="position-hint">Drag text on preview or set coordinates:</p>
                  <div className="input-row">
                    <Input label="X" type="number" value={Math.round(selectedItem.x)} onChange={(e) => updateTextItem(selectedItemId, { x: parseFloat(e.target.value) || 0 })} />
                    <Input label="Y" type="number" value={Math.round(selectedItem.y)} onChange={(e) => updateTextItem(selectedItemId, { y: parseFloat(e.target.value) || 0 })} />
                  </div>
                </div>
              )}
            </>
          )}

          {file && textItems.length > 0 && (
            <div className="sidebar-actions">
              <Button onClick={handleAddAllText} loading={loading} disabled={loading} icon={<Download size={20} />} fullWidth size="lg">
                {loading ? 'Adding Text...' : 'Apply & Download'}
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
                  {currentPageItems.map((item) => (
                    <div key={item.id} className={`text-overlay ${selectedItemId === item.id ? 'selected' : ''}`}
                      style={{
                        left: item.x * scale,
                        bottom: item.y * scale,
                        fontSize: item.fontSize * scale,
                        fontFamily: item.fontName.includes('COURIER') ? 'Courier' : item.fontName.includes('TIMES') ? 'Times New Roman' : 'Helvetica',
                        fontWeight: item.fontName.includes('BOLD') ? 'bold' : 'normal',
                        fontStyle: item.fontName.includes('ITALIC') || item.fontName.includes('OBLIQUE') ? 'italic' : 'normal',
                        color: item.fontColor,
                        cursor: 'move',
                      }}
                      onMouseDown={(e) => handleMouseDown(e, item)}>
                      {item.text}
                    </div>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Type size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Add text at any position on any page</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default AddTextPage;
