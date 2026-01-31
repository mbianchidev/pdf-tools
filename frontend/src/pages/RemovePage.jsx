import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Trash2, ArrowLeft, Download } from 'lucide-react';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

const RemovePage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [selectedPages, setSelectedPages] = useState([]);
  const [pagesInput, setPagesInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [toasts, setToasts] = useState([]);

  const addToast = (message, type = 'success', duration = 5000) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const handleFilesChange = useCallback((files) => {
    setFile(files[0] || null);
    setSelectedPages([]);
    setPagesInput('');
  }, []);

  const handlePageSelect = (pageNum) => {
    setSelectedPages((prev) => {
      const newSelection = prev.includes(pageNum)
        ? prev.filter((p) => p !== pageNum)
        : [...prev, pageNum].sort((a, b) => a - b);
      
      setPagesInput(newSelection.join(','));
      return newSelection;
    });
  };

  const handlePagesInputChange = (e) => {
    const value = e.target.value;
    setPagesInput(value);
    
    try {
      const pages = [];
      value.split(',').forEach((part) => {
        const trimmed = part.trim();
        if (trimmed.includes('-')) {
          const [start, end] = trimmed.split('-').map((n) => parseInt(n.trim()));
          if (!isNaN(start) && !isNaN(end)) {
            for (let i = start; i <= end; i++) {
              if (!pages.includes(i)) pages.push(i);
            }
          }
        } else {
          const num = parseInt(trimmed);
          if (!isNaN(num) && !pages.includes(num)) {
            pages.push(num);
          }
        }
      });
      setSelectedPages(pages.sort((a, b) => a - b));
    } catch {
      // Invalid input
    }
  };

  const handleRemove = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    if (selectedPages.length === 0 && !pagesInput) {
      addToast('Please select pages to remove', 'error');
      return;
    }

    setLoading(true);
    try {
      const pages = pagesInput || selectedPages.join(',');
      const result = await pdfService.removePages(file, pages);
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_removed.pdf`);
      addToast('Pages removed successfully!', 'success');
    } catch (error) {
      console.error('Remove error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to remove pages',
        'error'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="operation-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <Trash2 size={28} />
          <h1>Remove Pages</h1>
        </div>
        <p className="operation-description">
          Select pages to remove from your PDF. Click on thumbnails or enter page numbers.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">Upload PDF</h3>
            <FileUpload
              onFilesChange={handleFilesChange}
              files={file ? [file] : []}
              multiple={false}
            />
          </div>

          {file && (
            <div className="sidebar-section">
              <h3 className="sidebar-title">Pages to Remove</h3>
              <Input
                label="Page Numbers"
                placeholder="e.g., 2,4,6-8"
                value={pagesInput}
                onChange={handlePagesInputChange}
                helperText="These pages will be deleted"
              />
              {selectedPages.length > 0 && (
                <div className="selected-pages-info warning">
                  Will remove: {selectedPages.length} page(s)
                </div>
              )}
            </div>
          )}

          {file && (
            <div className="sidebar-actions">
              <Button
                onClick={handleRemove}
                loading={loading}
                disabled={loading || (selectedPages.length === 0 && !pagesInput)}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Removing...' : 'Remove & Download'}
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <h3>Preview: {file.name}</h3>
                <span className="preview-hint">Click thumbnails to select pages to remove</span>
              </div>
              <PdfViewer 
                file={file} 
                showThumbnails={true}
                selectable={true}
                selectedPages={selectedPages}
                onPageSelect={handlePageSelect}
              />
            </>
          ) : (
            <div className="preview-empty">
              <Trash2 size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Select pages to remove from the document</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default RemovePage;
