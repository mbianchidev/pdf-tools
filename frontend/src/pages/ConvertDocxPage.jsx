import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileType, ArrowLeft, Download, Eye } from 'lucide-react';
import mammoth from 'mammoth';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';
import './ConvertPage.css';

const ConvertDocxPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [docxBlob, setDocxBlob] = useState(null);
  const [htmlContent, setHtmlContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [converted, setConverted] = useState(false);
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
    setHtmlContent('');
    setDocxBlob(null);
    setConverted(false);
  }, []);

  const handleConvert = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.convertToDocx(file);
      setDocxBlob(result);
      
      // Convert DOCX blob to HTML for preview using mammoth
      const arrayBuffer = await result.arrayBuffer();
      const mammothResult = await mammoth.convertToHtml({ arrayBuffer });
      setHtmlContent(mammothResult.value);
      
      setConverted(true);
      addToast('PDF converted to DOCX!', 'success');
    } catch (error) {
      console.error('Convert error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to convert PDF',
        'error'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = () => {
    if (docxBlob) {
      const baseName = file.name.replace(/\\.[pP][dD][fF]$/, '');
      downloadBlob(docxBlob, `${baseName}_docx.docx`);
      addToast('DOCX file downloaded!', 'success');
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
          <FileType size={28} />
          <h1>Convert to DOCX</h1>
        </div>
        <p className="operation-description">
          Convert your PDF to Microsoft Word format.
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

          {file && !converted && (
            <div className="sidebar-actions">
              <Button
                onClick={handleConvert}
                loading={loading}
                disabled={loading}
                icon={<Eye size={20} />}
                fullWidth
                size="lg"
              >
                {loading ? 'Converting...' : 'Convert to DOCX'}
              </Button>
            </div>
          )}

          {converted && (
            <div className="sidebar-actions">
              <Button
                onClick={handleDownload}
                icon={<Download size={20} />}
                fullWidth
                size="lg"
              >
                Download DOCX
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {converted ? (
            <div className="docx-preview">
              <div className="preview-header">
                <h3>DOCX Preview</h3>
              </div>
              <div 
                className="docx-content"
                dangerouslySetInnerHTML={{ __html: htmlContent }}
              />
            </div>
          ) : file ? (
            <>
              <div className="preview-header">
                <h3>Preview: {file.name}</h3>
              </div>
              <PdfViewer file={file} showThumbnails={true} />
            </>
          ) : (
            <div className="preview-empty">
              <FileType size={64} />
              <h3>Upload a PDF to convert</h3>
              <p>Convert PDF to Microsoft Word format</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default ConvertDocxPage;
