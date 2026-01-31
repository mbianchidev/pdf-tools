import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileOutput, ArrowLeft, Download } from 'lucide-react';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

const ExtractPage = () => {
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
      
      // Update the text input to match selection
      setPagesInput(newSelection.join(','));
      return newSelection;
    });
  };

  const handlePagesInputChange = (e) => {
    const value = e.target.value;
    setPagesInput(value);
    
    // Parse the input and update selected pages
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
      // Invalid input, ignore
    }
  };

  const handleExtract = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    if (selectedPages.length === 0 && !pagesInput) {
      addToast('Please select pages to extract', 'error');
      return;
    }

    setLoading(true);
    try {
      const pages = pagesInput || selectedPages.join(',');
      const result = await pdfService.extractPages(file, pages);
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_extracted.pdf`);
      addToast('Pages extracted successfully!', 'success');
    } catch (error) {
      console.error('Extract error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to extract pages',
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
          <FileOutput size={28} />
          <h1>Extract Pages</h1>
        </div>
        <p className="operation-description">
          Select specific pages to extract from your PDF. Click on thumbnails or enter page numbers.
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
              <h3 className="sidebar-title">Pages to Extract</h3>
              <Input
                label="Page Numbers"
                placeholder="e.g., 1,3,5-7"
                value={pagesInput}
                onChange={handlePagesInputChange}
                helperText="Enter pages or click thumbnails to select"
              />
              {selectedPages.length > 0 && (
                <div className="selected-pages-info">
                  Selected: {selectedPages.length} page(s)
                </div>
              )}
            </div>
          )}

          {file && (
            <div className="sidebar-actions">
              <Button
                onClick={handleExtract}
                loading={loading}
                disabled={loading || (selectedPages.length === 0 && !pagesInput)}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Extracting...' : 'Extract & Download'}
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <h3>Preview: {file.name}</h3>
                <span className="preview-hint">Click thumbnails to select pages</span>
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
              <FileOutput size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Select pages to extract into a new PDF</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default ExtractPage;
