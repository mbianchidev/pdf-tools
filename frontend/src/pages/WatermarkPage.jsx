import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Droplet, ArrowLeft, Download } from 'lucide-react';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

const WatermarkPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [watermarkText, setWatermarkText] = useState('');
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

  const handleWatermark = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    if (!watermarkText.trim()) {
      addToast('Please enter watermark text', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.addWatermark(file, watermarkText);
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_watermarked.pdf`);
      addToast('Watermark added successfully!', 'success');
    } catch (error) {
      console.error('Watermark error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to add watermark',
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
          <Droplet size={28} />
          <h1>Add Watermark</h1>
        </div>
        <p className="operation-description">
          Add a text watermark to all pages of your PDF.
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
              <h3 className="sidebar-title">Watermark Settings</h3>
              <Input
                label="Watermark Text"
                placeholder="e.g., CONFIDENTIAL, DRAFT, etc."
                value={watermarkText}
                onChange={(e) => setWatermarkText(e.target.value)}
              />
            </div>
          )}

          {file && (
            <div className="sidebar-actions">
              <Button
                onClick={handleWatermark}
                loading={loading}
                disabled={loading || !watermarkText.trim()}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Adding Watermark...' : 'Add Watermark & Download'}
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
              <Droplet size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Add a diagonal watermark text across all pages</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default WatermarkPage;
