import React, { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Combine, Download, Trash2, GripVertical, ArrowUp, ArrowDown, Eye, FileText } from 'lucide-react';
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
import './MergePage.css';

const MergePage = () => {
  const navigate = useNavigate();
  const [files, setFiles] = useState([]);
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [selectedFileUrl, setSelectedFileUrl] = useState(null);
  const [loading, setLoading] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [toasts, setToasts] = useState([]);
  
  // Result preview state
  const [mergedBlob, setMergedBlob] = useState(null);
  const [mergedUrl, setMergedUrl] = useState(null);
  const [showingResult, setShowingResult] = useState(false);
  const [resultNumPages, setResultNumPages] = useState(null);
  const [resultPage, setResultPage] = useState(1);
  const [resultScale, setResultScale] = useState(1);

  const addToast = (message, type = 'success', duration = 5000) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  // Update selected file URL when selection changes
  useEffect(() => {
    if (files.length > 0 && files[selectedFileIndex]) {
      const url = URL.createObjectURL(files[selectedFileIndex]);
      setSelectedFileUrl(url);
      return () => URL.revokeObjectURL(url);
    } else {
      setSelectedFileUrl(null);
    }
  }, [files, selectedFileIndex]);

  // Cleanup merged URL on unmount
  useEffect(() => {
    return () => {
      if (mergedUrl) URL.revokeObjectURL(mergedUrl);
    };
  }, [mergedUrl]);

  const handleFilesChange = useCallback((newFiles) => {
    setFiles(newFiles);
    setMergedBlob(null);
    setMergedUrl(null);
    setShowingResult(false);
    if (newFiles.length > 0 && selectedFileIndex >= newFiles.length) {
      setSelectedFileIndex(newFiles.length - 1);
    }
  }, [selectedFileIndex]);

  const moveFile = (fromIndex, toIndex) => {
    if (toIndex < 0 || toIndex >= files.length) return;
    const newFiles = [...files];
    const [movedFile] = newFiles.splice(fromIndex, 1);
    newFiles.splice(toIndex, 0, movedFile);
    setFiles(newFiles);
    setSelectedFileIndex(toIndex);
    setMergedBlob(null);
    setMergedUrl(null);
    setShowingResult(false);
  };

  const removeFile = (index) => {
    const newFiles = files.filter((_, i) => i !== index);
    setFiles(newFiles);
    setMergedBlob(null);
    setMergedUrl(null);
    setShowingResult(false);
    if (selectedFileIndex >= newFiles.length && newFiles.length > 0) {
      setSelectedFileIndex(newFiles.length - 1);
    } else if (newFiles.length === 0) {
      setSelectedFileIndex(0);
    }
  };

  const handlePreviewResult = async () => {
    if (files.length < 2) {
      addToast('Please upload at least 2 PDF files', 'error');
      return;
    }

    setPreviewLoading(true);
    try {
      const result = await pdfService.merge(files);
      setMergedBlob(result);
      const url = URL.createObjectURL(result);
      setMergedUrl(url);
      setShowingResult(true);
      setResultPage(1);
      addToast('Preview generated!', 'success');
    } catch (error) {
      console.error('Preview error:', error);
      addToast(error.response?.data?.message || error.message || 'Failed to generate preview', 'error');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleDownload = () => {
    if (mergedBlob) {
      const baseName = files[0].name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(mergedBlob, `${baseName}_merged.pdf`);
      addToast('Downloaded!', 'success');
    }
  };

  const handleMerge = async () => {
    if (files.length < 2) {
      addToast('Please upload at least 2 PDF files to merge', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.merge(files);
      const baseName = files[0].name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_merged.pdf`);
      addToast('PDFs merged successfully!', 'success');
    } catch (error) {
      console.error('Merge error:', error);
      addToast(error.response?.data?.message || error.message || 'Failed to merge PDFs', 'error');
    } finally {
      setLoading(false);
    }
  };

  const onResultLoadSuccess = ({ numPages }) => {
    setResultNumPages(numPages);
    setResultPage(1);
  };

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} /><span>Back</span>
        </button>
        <div className="operation-title">
          <Combine size={28} /><h1>Merge PDFs</h1>
        </div>
        <p className="operation-description">Combine multiple PDF files into a single document. Drag to reorder files.</p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">PDF Files ({files.length})</h3>
            
            {files.length === 0 ? (
              <FileUpload onFilesChange={handleFilesChange} files={files} multiple={true} maxFiles={20} />
            ) : (
              <>
                <div className="file-order-list">
                  {files.map((file, index) => (
                    <motion.div key={`${file.name}-${index}`}
                      className={`file-order-item ${selectedFileIndex === index && !showingResult ? 'active' : ''}`}
                      initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: index * 0.05 }}>
                      <div className="file-order-grip"><GripVertical size={16} /></div>
                      <div className="file-order-info" onClick={() => { setSelectedFileIndex(index); setShowingResult(false); }}>
                        <span className="file-order-number">{index + 1}</span>
                        <span className="file-order-name">{file.name}</span>
                        <span className="file-order-size">{(file.size / 1024 / 1024).toFixed(2)} MB</span>
                      </div>
                      <div className="file-order-actions">
                        <button className="file-order-btn" onClick={() => moveFile(index, index - 1)} disabled={index === 0} title="Move up"><ArrowUp size={14} /></button>
                        <button className="file-order-btn" onClick={() => moveFile(index, index + 1)} disabled={index === files.length - 1} title="Move down"><ArrowDown size={14} /></button>
                        <button className="file-order-btn delete" onClick={() => removeFile(index)} title="Remove"><Trash2 size={14} /></button>
                      </div>
                    </motion.div>
                  ))}
                </div>
                <div className="add-more-files">
                  <FileUpload onFilesChange={(newFiles) => { setFiles([...files, ...newFiles]); setMergedBlob(null); setShowingResult(false); }} files={[]} multiple={true} maxFiles={20 - files.length} />
                </div>
              </>
            )}
          </div>

          {files.length >= 2 && (
            <div className="sidebar-actions">
              <Button onClick={handlePreviewResult} loading={previewLoading} disabled={previewLoading || files.length < 2}
                icon={<Eye size={20} />} fullWidth size="lg" variant="secondary">
                {previewLoading ? 'Generating...' : 'Preview Result'}
              </Button>
              {mergedBlob ? (
                <Button onClick={handleDownload} icon={<Download size={20} />} fullWidth size="lg" style={{ marginTop: 8 }}>
                  Download Merged PDF
                </Button>
              ) : (
                <Button onClick={handleMerge} loading={loading} disabled={loading || files.length < 2}
                  icon={<Download size={20} />} fullWidth size="lg" style={{ marginTop: 8 }}>
                  {loading ? 'Merging...' : 'Merge & Download'}
                </Button>
              )}
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {showingResult && mergedUrl ? (
            <>
              <div className="preview-header result-header">
                <h3><FileText size={18} /> Merged Result Preview</h3>
                <div className="page-nav">
                  <button disabled={resultPage <= 1} onClick={() => setResultPage(p => p - 1)}>←</button>
                  <span>Page {resultPage} of {resultNumPages || '?'}</span>
                  <button disabled={resultPage >= (resultNumPages || 1)} onClick={() => setResultPage(p => p + 1)}>→</button>
                </div>
              </div>
              <div className="pdf-preview-container">
                <Document file={mergedUrl} onLoadSuccess={onResultLoadSuccess} loading={<div className="loading-placeholder">Loading merged PDF...</div>}>
                  <Page pageNumber={resultPage} scale={resultScale} renderTextLayer={false} renderAnnotationLayer={false} />
                </Document>
              </div>
            </>
          ) : files.length > 0 && selectedFileUrl ? (
            <>
              <div className="preview-header">
                <h3>Preview: {files[selectedFileIndex]?.name}</h3>
                <span className="preview-position">File {selectedFileIndex + 1} of {files.length}</span>
              </div>
              <div className="pdf-preview-container">
                <Document file={selectedFileUrl} loading={<div className="loading-placeholder">Loading PDF...</div>}>
                  <Page pageNumber={1} scale={1} renderTextLayer={false} renderAnnotationLayer={false} />
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Combine size={64} />
              <h3>Upload PDF files to preview</h3>
              <p>Upload at least 2 PDF files to merge them into one document</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default MergePage;
