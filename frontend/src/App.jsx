import React from 'react';
import { Routes, Route, useNavigate } from 'react-router-dom';
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
} from 'lucide-react';
import {
  MergePage,
  SplitPage,
  ExtractPage,
  RemovePage,
  WatermarkPage,
  AddTextPage,
  SignaturePage,
  RedactPage,
  ConvertMarkdownPage,
  ConvertDocxPage,
} from './pages';
import './App.css';

const operations = [
  {
    id: 'merge',
    path: '/merge',
    icon: <Combine size={28} />,
    title: 'Merge PDFs',
    description: 'Combine multiple PDF files into a single document',
  },
  {
    id: 'split',
    path: '/split',
    icon: <Scissors size={28} />,
    title: 'Split PDF',
    description: 'Split a PDF into multiple documents',
  },
  {
    id: 'extract',
    path: '/extract',
    icon: <FileOutput size={28} />,
    title: 'Extract Pages',
    description: 'Extract specific pages from a PDF',
  },
  {
    id: 'remove',
    path: '/remove',
    icon: <Trash2 size={28} />,
    title: 'Remove Pages',
    description: 'Remove specific pages from a PDF',
  },
  {
    id: 'watermark',
    path: '/watermark',
    icon: <Droplet size={28} />,
    title: 'Add Watermark',
    description: 'Add a text watermark to all pages',
  },
  {
    id: 'add-text',
    path: '/add-text',
    icon: <Type size={28} />,
    title: 'Add/Edit Text',
    description: 'Add or edit custom text at a specific position',
  },
  {
    id: 'add-signature',
    path: '/signature',
    icon: <PenTool size={28} />,
    title: 'Add Signature',
    description: 'Add a signature image to your PDF',
  },
  {
    id: 'redact',
    path: '/redact',
    icon: <EyeOff size={28} />,
    title: 'Redact Content',
    description: 'Redact sensitive information from your PDF',
  },
  {
    id: 'convert-markdown',
    path: '/convert-markdown',
    icon: <FileText size={28} />,
    title: 'Convert to Markdown',
    description: 'Convert PDF to Markdown format',
  },
  {
    id: 'convert-docx',
    path: '/convert-docx',
    icon: <FileType size={28} />,
    title: 'Convert to DOCX',
    description: 'Convert PDF to Microsoft Word format',
  },
];

function HomePage() {
  const navigate = useNavigate();

  return (
    <div className="app">
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
                <div
                  className="operation-card-link"
                  onClick={() => navigate(op.path)}
                >
                  <div className="operation-card">
                    <div className="operation-card-header">
                      <div className="operation-card-icon">{op.icon}</div>
                      <div className="operation-card-info">
                        <h3 className="operation-card-title">{op.title}</h3>
                        <p className="operation-card-description">{op.description}</p>
                      </div>
                    </div>
                  </div>
                </div>
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

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/merge" element={<MergePage />} />
      <Route path="/split" element={<SplitPage />} />
      <Route path="/extract" element={<ExtractPage />} />
      <Route path="/remove" element={<RemovePage />} />
      <Route path="/watermark" element={<WatermarkPage />} />
      <Route path="/add-text" element={<AddTextPage />} />
      <Route path="/signature" element={<SignaturePage />} />
      <Route path="/redact" element={<RedactPage />} />
      <Route path="/convert-markdown" element={<ConvertMarkdownPage />} />
      <Route path="/convert-docx" element={<ConvertDocxPage />} />
    </Routes>
  );
}

export default App;
