import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Type, ArrowLeft, Download } from 'lucide-react';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

const AddTextPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [textContent, setTextContent] = useState('');
  const [xPosition, setXPosition] = useState('100');
  const [yPosition, setYPosition] = useState('700');
  const [pageNumber, setPageNumber] = useState('1');
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
  }, []);

  const handleAddText = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    if (!textContent.trim()) {
      addToast('Please enter text to add', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.addText(
        file,
        textContent,
        parseFloat(xPosition),
        parseFloat(yPosition),
        parseInt(pageNumber)
      );
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_text_added.pdf`);
      addToast('Text added successfully!', 'success');
    } catch (error) {
      console.error('Add text error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to add text',
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
          <Type size={28} />
          <h1>Add/Edit Text</h1>
        </div>
        <p className="operation-description">
          Add custom text at a specific position on your PDF.
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
              <h3 className="sidebar-title">Text Settings</h3>
              <Input
                label="Text Content"
                placeholder="Enter text to add"
                value={textContent}
                onChange={(e) => setTextContent(e.target.value)}
              />
              <div className="input-row">
                <Input
                  label="X Position"
                  type="number"
                  value={xPosition}
                  onChange={(e) => setXPosition(e.target.value)}
                />
                <Input
                  label="Y Position"
                  type="number"
                  value={yPosition}
                  onChange={(e) => setYPosition(e.target.value)}
                />
              </div>
              <Input
                label="Page Number"
                type="number"
                min="1"
                value={pageNumber}
                onChange={(e) => setPageNumber(e.target.value)}
              />
            </div>
          )}

          {file && (
            <div className="sidebar-actions">
              <Button
                onClick={handleAddText}
                loading={loading}
                disabled={loading || !textContent.trim()}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Adding Text...' : 'Add Text & Download'}
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <h3>Preview: {file.name}</h3>
              </div>
              <PdfViewer file={file} showThumbnails={true} />
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
