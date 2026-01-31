import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PenTool, ArrowLeft, Download } from 'lucide-react';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import Input from '../components/Input';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

const SignaturePage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [signatureFile, setSignatureFile] = useState(null);
  const [xPosition, setXPosition] = useState('400');
  const [yPosition, setYPosition] = useState('100');
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

  const handleSignatureChange = useCallback((files) => {
    setSignatureFile(files[0] || null);
  }, []);

  const handleAddSignature = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    if (!signatureFile) {
      addToast('Please upload a signature image', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.addSignature(
        file,
        signatureFile,
        parseFloat(xPosition),
        parseFloat(yPosition),
        parseInt(pageNumber)
      );
      const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
      downloadBlob(result, `${baseName}_signed.pdf`);
      addToast('Signature added successfully!', 'success');
    } catch (error) {
      console.error('Signature error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to add signature',
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
          <PenTool size={28} />
          <h1>Add Signature</h1>
        </div>
        <p className="operation-description">
          Add a signature image to your PDF at a specific position.
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
            <>
              <div className="sidebar-section">
                <h3 className="sidebar-title">Signature Image</h3>
                <FileUpload
                  onFilesChange={handleSignatureChange}
                  files={signatureFile ? [signatureFile] : []}
                  accept={{ 'image/*': ['.png', '.jpg', '.jpeg'] }}
                  multiple={false}
                />
              </div>

              <div className="sidebar-section">
                <h3 className="sidebar-title">Position</h3>
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
            </>
          )}

          {file && signatureFile && (
            <div className="sidebar-actions">
              <Button
                onClick={handleAddSignature}
                loading={loading}
                disabled={loading}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Adding Signature...' : 'Add Signature & Download'}
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
              <PenTool size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Add your signature image to any page</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default SignaturePage;
