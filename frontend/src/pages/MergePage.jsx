import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Combine, Download, Trash2, GripVertical, ArrowUp, ArrowDown } from 'lucide-react';
import { Document, Page } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import '../lib/pdfWorker';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import ToastContainer from '../components/Toast';
import JobProgress from '../features/jobs/JobProgress';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { jobService, getApiErrorMessage } from '../services/jobService';
import { downloadBlob } from '../services/pdfService';
import './OperationPage.css';
import './MergePage.css';

const MAX_MERGE_BYTES = 100 * 1024 * 1024;

const MergePage = () => {
  const navigate = useNavigate();
  const [files, setFiles] = useState([]);
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [selectedFileUrl, setSelectedFileUrl] = useState(null);
  const [toasts, setToasts] = useState([]);
  const [dragIndex, setDragIndex] = useState(null);
  const [failedOutput, setFailedOutput] = useState(null);
  const [downloadingOutput, setDownloadingOutput] = useState(false);
  const urlRef = useRef(null);
  const handledJobRef = useRef(null);
  const {
    job,
    running,
    connectionError,
    start,
    cancel,
    reset,
  } = usePdfJob();
  const totalBytes = useMemo(
    () => files.reduce((total, file) => total + file.size, 0),
    [files],
  );

  const addToast = useCallback((message, type = 'success', duration = 5000) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const downloadOutput = useCallback(async (output) => {
    setDownloadingOutput(true);
    try {
      const blob = await jobService.download(output);
      downloadBlob(blob, output.filename);
      setFailedOutput(null);
      addToast('PDFs merged successfully!', 'success');
    } catch (error) {
      console.error('Merge download error:', error);
      setFailedOutput(output);
      addToast(getApiErrorMessage(error, 'Failed to download merged PDF'), 'error');
    } finally {
      setDownloadingOutput(false);
    }
  }, [addToast]);

  const updatePreviewUrl = useCallback((index, fileList) => {
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
      urlRef.current = null;
    }
    if (fileList.length > 0 && fileList[index]) {
      const url = URL.createObjectURL(fileList[index]);
      urlRef.current = url;
      setSelectedFileUrl(url);
    } else {
      setSelectedFileUrl(null);
    }
  }, []);

  // Cleanup object URL on unmount
  useEffect(() => {
    return () => {
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
      }
    };
  }, []);

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
        addToast(job.errorMessage || 'Failed to merge PDFs', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF merge cancelled', 'error');
        return;
      }

      const output = job.outputs[0];
      if (!output) {
        addToast('The merge job completed without an output.', 'error');
        return;
      }
      await downloadOutput(output);
    };
    handleResult();
    return () => {
      active = false;
    };
  }, [addToast, downloadOutput, job]);

  const handleFilesChange = useCallback((newFiles) => {
    if (running) return;
    reset();
    handledJobRef.current = null;
    setFailedOutput(null);
    setFiles(newFiles);
    let newIndex = selectedFileIndex;
    if (newFiles.length === 0) {
      newIndex = 0;
      setSelectedFileIndex(0);
    } else if (selectedFileIndex >= newFiles.length) {
      newIndex = newFiles.length - 1;
      setSelectedFileIndex(newIndex);
    }
    updatePreviewUrl(newIndex, newFiles);
  }, [reset, running, selectedFileIndex, updatePreviewUrl]);

  const moveFile = (fromIndex, toIndex) => {
    if (running || toIndex < 0 || toIndex >= files.length) return;
    reset();
    handledJobRef.current = null;
    setFailedOutput(null);
    const newFiles = [...files];
    const [movedFile] = newFiles.splice(fromIndex, 1);
    newFiles.splice(toIndex, 0, movedFile);
    setFiles(newFiles);
    setSelectedFileIndex(toIndex);
    updatePreviewUrl(toIndex, newFiles);
  };

  const removeFile = (index) => {
    if (running) return;
    reset();
    handledJobRef.current = null;
    setFailedOutput(null);
    const newFiles = files.filter((_, i) => i !== index);
    setFiles(newFiles);
    let newIndex = selectedFileIndex;
    if (newFiles.length === 0) {
      newIndex = 0;
      setSelectedFileIndex(0);
    } else if (selectedFileIndex >= newFiles.length) {
      newIndex = newFiles.length - 1;
      setSelectedFileIndex(newIndex);
    } else if (index < selectedFileIndex) {
      newIndex = selectedFileIndex - 1;
      setSelectedFileIndex(newIndex);
    }
    updatePreviewUrl(newIndex, newFiles);
  };

  const handleMerge = async () => {
    if (files.length < 2) {
      addToast('Please upload at least 2 PDF files to merge', 'error');
      return;
    }
    if (totalBytes > MAX_MERGE_BYTES) {
      addToast('Merge inputs must stay within the 100 MB total limit', 'error');
      return;
    }

    const invalidFile = files.find((file) => !file.name.toLowerCase().endsWith('.pdf'));
    if (invalidFile) {
      addToast(`${invalidFile.name} is not a PDF file`, 'error');
      return;
    }

    handledJobRef.current = null;
    setFailedOutput(null);
    try {
      await start('merge', files, {});
    } catch (error) {
      console.error('Merge error:', error);
      addToast(getApiErrorMessage(error, 'Failed to merge PDFs'), 'error');
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Merge cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel merge'), 'error');
    }
  };

  const handleDrop = (targetIndex) => {
    if (dragIndex !== null) {
      moveFile(dragIndex, targetIndex);
    }
    setDragIndex(null);
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
                    <motion.div key={`${file.name}-${file.size}-${file.lastModified}-${index}`}
                      className={`file-order-item ${selectedFileIndex === index ? 'active' : ''}`}
                      draggable={!running}
                      onDragStart={() => setDragIndex(index)}
                      onDragOver={(event) => event.preventDefault()}
                      onDrop={() => handleDrop(index)}
                      initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: index * 0.05 }}>
                      <div className="file-order-grip"><GripVertical size={16} /></div>
                      <div className="file-order-info" onClick={() => { setSelectedFileIndex(index); updatePreviewUrl(index, files); }}>
                        <span className="file-order-number">{index + 1}</span>
                        <span className="file-order-name">{file.name}</span>
                        <span className="file-order-size">{(file.size / 1024 / 1024).toFixed(2)} MB</span>
                      </div>
                      <div className="file-order-actions">
                        <button className="file-order-btn" onClick={() => moveFile(index, index - 1)} disabled={running || index === 0} aria-label={`Move ${file.name} up`}><ArrowUp size={14} /></button>
                        <button className="file-order-btn" onClick={() => moveFile(index, index + 1)} disabled={running || index === files.length - 1} aria-label={`Move ${file.name} down`}><ArrowDown size={14} /></button>
                        <button className="file-order-btn delete" onClick={() => removeFile(index)} disabled={running} aria-label={`Remove ${file.name}`}><Trash2 size={14} /></button>
                      </div>
                    </motion.div>
                  ))}
                </div>
                <div className={`merge-size ${totalBytes > MAX_MERGE_BYTES ? 'over-limit' : ''}`}>
                  {(totalBytes / 1024 / 1024).toFixed(2)} MB of 100 MB
                </div>
                {!running && files.length < 20 && <div className="add-more-files">
                  <FileUpload onFilesChange={(newFiles) => handleFilesChange([...files, ...newFiles])} files={[]} multiple={true} maxFiles={20 - files.length} />
                </div>}
              </>
            )}
          </div>

          {files.length >= 2 && (
            <div className="sidebar-actions">
              {job && (
                <JobProgress
                  job={job}
                  connectionError={connectionError}
                  onCancel={handleCancel}
                />
              )}
              {failedOutput && (
                <Button
                  onClick={() => downloadOutput(failedOutput)}
                  loading={downloadingOutput}
                  disabled={downloadingOutput}
                  variant="outline"
                  icon={<Download size={20} />}
                  fullWidth
                >
                  {downloadingOutput ? 'Downloading...' : 'Retry download'}
                </Button>
              )}
              <Button onClick={handleMerge} loading={running} disabled={running || files.length < 2 || totalBytes > MAX_MERGE_BYTES}
                icon={<Download size={20} />} fullWidth size="lg">
                {running ? 'Merging...' : 'Merge & Download'}
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
