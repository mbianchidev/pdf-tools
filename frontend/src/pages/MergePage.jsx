import React, { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
// eslint-disable-next-line no-unused-vars
import { motion } from 'framer-motion';
import { ArrowLeft, Combine, Download, Trash2, GripVertical, ArrowUp, ArrowDown } from 'lucide-react';
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
  const [toasts, setToasts] = useState([]);

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

  const handleFilesChange = useCallback((newFiles) => {
    setFiles(newFiles);
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
  };

  const removeFile = (index) => {
    const newFiles = files.filter((_, i) => i !== index);
    setFiles(newFiles);
    if (selectedFileIndex >= newFiles.length && newFiles.length > 0) {
      setSelectedFileIndex(newFiles.length - 1);
    } else if (newFiles.length === 0) {
      setSelectedFileIndex(0);
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
                      className={`file-order-item ${selectedFileIndex === index ? 'active' : ''}`}
                      initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: index * 0.05 }}>
                      <div className="file-order-grip"><GripVertical size={16} /></div>
                      <div className="file-order-info" onClick={() => setSelectedFileIndex(index)}>
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
                  <FileUpload onFilesChange={(newFiles) => { setFiles([...files, ...newFiles]); }} files={[]} multiple={true} maxFiles={20 - files.length} />
                </div>
              </>
            )}
          </div>

          {files.length >= 2 && (
            <div className="sidebar-actions">
              <Button onClick={handleMerge} loading={loading} disabled={loading || files.length < 2}
                icon={<Download size={20} />} fullWidth size="lg">
                {loading ? 'Merging...' : 'Merge & Download'}
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {files.length > 0 && selectedFileUrl ? (
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
