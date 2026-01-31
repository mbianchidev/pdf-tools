import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { EyeOff, ArrowLeft, Download } from 'lucide-react';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

const RedactPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [xPosition, setXPosition] = useState('100');
  const [yPosition, setYPosition] = useState('700');
  const [width, setWidth] = useState('200');
  const [height, setHeight] = useState('20');
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

  const handleRedact = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.redact(
        file,
        parseFloat(xPosition),
        parseFloat(yPosition),
        parseFloat(width),
        parseFloat(height),
        parseInt(pageNumber)
      );
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_redacted.pdf`);
      addToast('Content redacted successfully!', 'success');
    } catch (error) {
      console.error('Redact error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to redact content',
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
          <EyeOff size={28} />
          <h1>Redact Content</h1>
        </div>
        <p className="operation-description">
          Cover sensitive content with a black rectangle.
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
              <h3 className="sidebar-title">Redaction Area</h3>
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
              <div className="input-row">
                <Input
                  label="Width"
                  type="number"
                  value={width}
                  onChange={(e) => setWidth(e.target.value)}
                />
                <Input
                  label="Height"
                  type="number"
                  value={height}
                  onChange={(e) => setHeight(e.target.value)}
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
                onClick={handleRedact}
                loading={loading}
                disabled={loading}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Redacting...' : 'Redact & Download'}
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
              <EyeOff size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Black out sensitive information</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default RedactPage;
