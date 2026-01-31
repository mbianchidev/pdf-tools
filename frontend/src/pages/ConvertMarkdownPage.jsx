import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileText, ArrowLeft, Download, Eye } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import PdfViewer from '../components/PdfViewer';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';
import './ConvertPage.css';

const ConvertMarkdownPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [markdownContent, setMarkdownContent] = useState('');
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
    setMarkdownContent('');
    setConverted(false);
  }, []);

  const handleConvert = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    setLoading(true);
    try {
      const result = await pdfService.convertToMarkdown(file);
      // Read the blob as text to display
      const text = await result.text();
      setMarkdownContent(text);
      setConverted(true);
      addToast('PDF converted to Markdown!', 'success');
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
    const blob = new Blob([markdownContent], { type: 'text/markdown' });
    const baseName = file.name.replace(/\\.[pP][dD][fF]$/, '');
    downloadBlob(blob, `${baseName}_markdown.md`);
    addToast('Markdown file downloaded!', 'success');
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
          <FileText size={28} />
          <h1>Convert to Markdown</h1>
        </div>
        <p className="operation-description">
          Extract text from your PDF and convert it to Markdown format.
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
                {loading ? 'Converting...' : 'Convert to Markdown'}
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
                Download Markdown
              </Button>
            </div>
          )}
        </aside>

        <main className="operation-preview">
          {converted ? (
            <div className="markdown-preview">
              <div className="preview-header">
                <h3>Markdown Preview</h3>
                <div className="preview-tabs">
                  <span className="preview-tab active">Preview</span>
                </div>
              </div>
              <div className="markdown-content">
                <ReactMarkdown>{markdownContent}</ReactMarkdown>
              </div>
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
              <FileText size={64} />
              <h3>Upload a PDF to convert</h3>
              <p>Convert PDF text content to Markdown format</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default ConvertMarkdownPage;
