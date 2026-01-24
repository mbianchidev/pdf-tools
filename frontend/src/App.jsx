import React, { useState } from 'react';
import { motion } from 'framer-motion';
import {
  Combine,
  Scissors,
  FileOutput,
  Trash2,
  Droplet,
  Type,
  PenTool,
  EyeOff,
  FileText,
  FileType,
  Download,
} from 'lucide-react';
import OperationCard from './components/OperationCard';
import FileUpload from './components/FileUpload';
import Button from './components/Button';
import Input from './components/Input';
import ToastContainer from './components/Toast';
import { pdfService, downloadBlob } from './services/pdfService';
import './App.css';

const operations = [
  {
    id: 'merge',
    icon: <Combine size={28} />,
    title: 'Merge PDFs',
    description: 'Combine multiple PDF files into a single document',
    multiple: true,
  },
  {
    id: 'split',
    icon: <Scissors size={28} />,
    title: 'Split PDF',
    description: 'Split a PDF into separate pages',
    multiple: false,
  },
  {
    id: 'extract',
    icon: <FileOutput size={28} />,
    title: 'Extract Pages',
    description: 'Extract specific pages from a PDF (e.g., "1,3,5-7")',
    multiple: false,
  },
  {
    id: 'remove',
    icon: <Trash2 size={28} />,
    title: 'Remove Pages',
    description: 'Remove specific pages from a PDF (e.g., "2,4,6-8")',
    multiple: false,
  },
  {
    id: 'watermark',
    icon: <Droplet size={28} />,
    title: 'Add Watermark',
    description: 'Add a text watermark to all pages',
    multiple: false,
  },
  {
    id: 'add-text',
    icon: <Type size={28} />,
    title: 'Add Text',
    description: 'Add custom text at a specific position',
    multiple: false,
  },
  {
    id: 'add-signature',
    icon: <PenTool size={28} />,
    title: 'Add Signature',
    description: 'Add a signature image to your PDF',
    multiple: false,
  },
  {
    id: 'redact',
    icon: <EyeOff size={28} />,
    title: 'Redact Content',
    description: 'Redact sensitive information from your PDF',
    multiple: false,
  },
  {
    id: 'convert-markdown',
    icon: <FileText size={28} />,
    title: 'Convert to Markdown',
    description: 'Convert PDF to Markdown format',
    multiple: false,
  },
  {
    id: 'convert-docx',
    icon: <FileType size={28} />,
    title: 'Convert to DOCX',
    description: 'Convert PDF to Microsoft Word format',
    multiple: false,
  },
];

function App() {
  const [activeOperation, setActiveOperation] = useState(null);
  const [files, setFiles] = useState([]);
  const [signatureFile, setSignatureFile] = useState([]);
  const [loading, setLoading] = useState(false);
  const [toasts, setToasts] = useState([]);
  
  // Form states for different operations
  const [pages, setPages] = useState('');
  const [watermarkText, setWatermarkText] = useState('');
  const [textContent, setTextContent] = useState('');
  const [xPosition, setXPosition] = useState('100');
  const [yPosition, setYPosition] = useState('100');
  const [pageNumber, setPageNumber] = useState('1');
  const [redactWidth, setRedactWidth] = useState('100');
  const [redactHeight, setRedactHeight] = useState('50');

  const addToast = (message, type = 'success', duration = 5000) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const resetForm = () => {
    setFiles([]);
    setSignatureFile([]);
    setPages('');
    setWatermarkText('');
    setTextContent('');
    setXPosition('100');
    setYPosition('100');
    setPageNumber('1');
    setRedactWidth('100');
    setRedactHeight('50');
  };

  const handleOperationClick = (operationId) => {
    if (activeOperation === operationId) {
      setActiveOperation(null);
      resetForm();
    } else {
      setActiveOperation(operationId);
      resetForm();
    }
  };

  const handleExecute = async () => {
    if (files.length === 0) {
      addToast('Please upload at least one file', 'error');
      return;
    }

    setLoading(true);
    try {
      let result;
      let filename = 'result.pdf';

      switch (activeOperation) {
        case 'merge':
          if (files.length < 2) {
            addToast('Please upload at least 2 files to merge', 'error');
            setLoading(false);
            return;
          }
          result = await pdfService.merge(files);
          filename = 'merged.pdf';
          break;

        case 'split':
          result = await pdfService.split(files[0]);
          filename = 'split.zip';
          break;

        case 'extract':
          if (!pages) {
            addToast('Please specify pages to extract', 'error');
            setLoading(false);
            return;
          }
          result = await pdfService.extractPages(files[0], pages);
          filename = 'extracted.pdf';
          break;

        case 'remove':
          if (!pages) {
            addToast('Please specify pages to remove', 'error');
            setLoading(false);
            return;
          }
          result = await pdfService.removePages(files[0], pages);
          filename = 'removed.pdf';
          break;

        case 'watermark':
          if (!watermarkText) {
            addToast('Please enter watermark text', 'error');
            setLoading(false);
            return;
          }
          result = await pdfService.addWatermark(files[0], watermarkText);
          filename = 'watermarked.pdf';
          break;

        case 'add-text':
          if (!textContent) {
            addToast('Please enter text to add', 'error');
            setLoading(false);
            return;
          }
          result = await pdfService.addText(
            files[0],
            textContent,
            parseFloat(xPosition),
            parseFloat(yPosition),
            parseInt(pageNumber)
          );
          filename = 'text-added.pdf';
          break;

        case 'add-signature':
          if (signatureFile.length === 0) {
            addToast('Please upload a signature image', 'error');
            setLoading(false);
            return;
          }
          result = await pdfService.addSignature(
            files[0],
            signatureFile[0],
            parseFloat(xPosition),
            parseFloat(yPosition),
            parseInt(pageNumber)
          );
          filename = 'signed.pdf';
          break;

        case 'redact':
          result = await pdfService.redact(
            files[0],
            parseFloat(xPosition),
            parseFloat(yPosition),
            parseFloat(redactWidth),
            parseFloat(redactHeight),
            parseInt(pageNumber)
          );
          filename = 'redacted.pdf';
          break;

        case 'convert-markdown':
          result = await pdfService.convertToMarkdown(files[0]);
          filename = 'converted.md';
          break;

        case 'convert-docx':
          result = await pdfService.convertToDocx(files[0]);
          filename = 'converted.docx';
          break;

        default:
          throw new Error('Invalid operation');
      }

      downloadBlob(result, filename);
      addToast('Operation completed successfully!', 'success');
      resetForm();
    } catch (error) {
      console.error('Error:', error);
      addToast(
        error.response?.data?.message || 'An error occurred. Please try again.',
        'error'
      );
    } finally {
      setLoading(false);
    }
  };

  const renderOperationContent = (op) => {
    const isActive = activeOperation === op.id;
    if (!isActive) return null;

    return (
      <div className="operation-content">
        <FileUpload
          onFilesChange={setFiles}
          files={files}
          multiple={op.multiple}
          maxFiles={op.multiple ? 10 : 1}
        />

        {/* Operation-specific inputs */}
        {(op.id === 'extract' || op.id === 'remove') && (
          <Input
            label="Page Numbers"
            placeholder="e.g., 1,3,5-7"
            value={pages}
            onChange={(e) => setPages(e.target.value)}
            helperText="Specify pages as comma-separated values or ranges"
          />
        )}

        {op.id === 'watermark' && (
          <Input
            label="Watermark Text"
            placeholder="Enter watermark text"
            value={watermarkText}
            onChange={(e) => setWatermarkText(e.target.value)}
          />
        )}

        {op.id === 'add-text' && (
          <>
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
                placeholder="100"
                value={xPosition}
                onChange={(e) => setXPosition(e.target.value)}
              />
              <Input
                label="Y Position"
                type="number"
                placeholder="100"
                value={yPosition}
                onChange={(e) => setYPosition(e.target.value)}
              />
              <Input
                label="Page Number"
                type="number"
                placeholder="1"
                value={pageNumber}
                onChange={(e) => setPageNumber(e.target.value)}
              />
            </div>
          </>
        )}

        {op.id === 'add-signature' && (
          <>
            <div className="signature-upload">
              <h4 className="signature-upload-title">Signature Image</h4>
              <FileUpload
                onFilesChange={setSignatureFile}
                files={signatureFile}
                accept={{ 'image/*': ['.png', '.jpg', '.jpeg'] }}
                multiple={false}
              />
            </div>
            <div className="input-row">
              <Input
                label="X Position"
                type="number"
                placeholder="100"
                value={xPosition}
                onChange={(e) => setXPosition(e.target.value)}
              />
              <Input
                label="Y Position"
                type="number"
                placeholder="100"
                value={yPosition}
                onChange={(e) => setYPosition(e.target.value)}
              />
              <Input
                label="Page Number"
                type="number"
                placeholder="1"
                value={pageNumber}
                onChange={(e) => setPageNumber(e.target.value)}
              />
            </div>
          </>
        )}

        {op.id === 'redact' && (
          <>
            <div className="input-row">
              <Input
                label="X Position"
                type="number"
                placeholder="100"
                value={xPosition}
                onChange={(e) => setXPosition(e.target.value)}
              />
              <Input
                label="Y Position"
                type="number"
                placeholder="100"
                value={yPosition}
                onChange={(e) => setYPosition(e.target.value)}
              />
            </div>
            <div className="input-row">
              <Input
                label="Width"
                type="number"
                placeholder="100"
                value={redactWidth}
                onChange={(e) => setRedactWidth(e.target.value)}
              />
              <Input
                label="Height"
                type="number"
                placeholder="50"
                value={redactHeight}
                onChange={(e) => setRedactHeight(e.target.value)}
              />
              <Input
                label="Page Number"
                type="number"
                placeholder="1"
                value={pageNumber}
                onChange={(e) => setPageNumber(e.target.value)}
              />
            </div>
          </>
        )}

        <Button
          onClick={handleExecute}
          loading={loading}
          disabled={loading}
          icon={<Download size={20} />}
          fullWidth
          size="lg"
        >
          {loading ? 'Processing...' : 'Process & Download'}
        </Button>
      </div>
    );
  };

  return (
    <div className="app">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      {/* Header */}
      <header className="app-header">
        <div className="container">
          <motion.div
            className="header-content"
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            <div className="header-logo">
              <div className="logo-icon">
                <FileText size={32} />
              </div>
              <h1 className="logo-text">PDF Tools</h1>
            </div>
            <p className="header-subtitle">
              Professional PDF manipulation made simple
            </p>
          </motion.div>
        </div>
      </header>

      {/* Main Content */}
      <main className="app-main">
        <div className="container">
          <div className="operations-grid">
            {operations.map((op, index) => (
              <motion.div
                key={op.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, delay: index * 0.05 }}
              >
                <OperationCard
                  icon={op.icon}
                  title={op.title}
                  description={op.description}
                  isActive={activeOperation === op.id}
                  onClick={() => handleOperationClick(op.id)}
                >
                  {renderOperationContent(op)}
                </OperationCard>
              </motion.div>
            ))}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="app-footer">
        <div className="container">
          <p className="footer-text">
            Built with React & Spring Boot • All processing happens on your server
          </p>
        </div>
      </footer>
    </div>
  );
}

export default App;
