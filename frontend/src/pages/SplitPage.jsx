import React, { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Scissors, ArrowLeft, Download, Plus, X, GripVertical } from 'lucide-react';
import { Document, Page, pdfjs } from 'react-pdf';
import FileUpload from '../components/FileUpload';
import Button from '../components/Button';
import ToastContainer from '../components/Toast';
import { pdfService, downloadBlob } from '../services/pdfService';
import './OperationPage.css';

// Set up PDF.js worker
pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

const SplitPage = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState(null);
  const [numPages, setNumPages] = useState(null);
  const [pageGroups, setPageGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [toasts, setToasts] = useState([]);
  const [splitMode, setSplitMode] = useState('individual'); // 'individual' or 'custom'

  const addToast = (message, type = 'success', duration = 5000) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type, duration }]);
  };

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const handleFilesChange = useCallback((files) => {
    setFile(files[0] || null);
    setPageGroups([]);
    setNumPages(null);
  }, []);

  const onDocumentLoadSuccess = ({ numPages: pages }) => {
    setNumPages(pages);
    // Initialize with one group containing all pages
    if (pages > 0) {
      setPageGroups([{ id: 1, pages: Array.from({ length: pages }, (_, i) => i + 1) }]);
    }
  };

  const addGroup = () => {
    const newId = pageGroups.length > 0 ? Math.max(...pageGroups.map(g => g.id)) + 1 : 1;
    setPageGroups([...pageGroups, { id: newId, pages: [] }]);
  };

  const removeGroup = (groupId) => {
    if (pageGroups.length > 1) {
      setPageGroups(pageGroups.filter(g => g.id !== groupId));
    }
  };

  const togglePageInGroup = (groupId, pageNum) => {
    setPageGroups(pageGroups.map(group => {
      if (group.id === groupId) {
        const hasPage = group.pages.includes(pageNum);
        if (hasPage) {
          return { ...group, pages: group.pages.filter(p => p !== pageNum) };
        } else {
          // Remove from other groups first
          return { ...group, pages: [...group.pages, pageNum].sort((a, b) => a - b) };
        }
      } else {
        // Remove page from other groups when adding to this one
        return { ...group, pages: group.pages.filter(p => p !== pageNum) };
      }
    }));
  };

  const handleSplit = async () => {
    if (!file) {
      addToast('Please upload a PDF file', 'error');
      return;
    }

    setLoading(true);
    try {
      let groups = null;
      if (splitMode === 'custom') {
        // Build groups string: "1,2,3;4,5" means pages 1-3 in one PDF, 4-5 in another
        const validGroups = pageGroups.filter(g => g.pages.length > 0);
        if (validGroups.length === 0) {
          addToast('Please assign pages to at least one group', 'error');
          setLoading(false);
          return;
        }
        groups = validGroups.map(g => g.pages.join(',')).join(';');
      }

      const result = await pdfService.split(file, groups);
      
      // For split, we might get multiple files
      if (result.filenames && result.filenames.length > 1) {
        // Download all files
        for (let i = 0; i < result.filenames.length; i++) {
          const blob = await pdfService.download(result.filenames[i]);
          const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
          downloadBlob(blob, `${baseName}_split_part${i + 1}.pdf`);
        }
        addToast(`PDF split into ${result.filenames.length} documents!`, 'success');
      } else {
        // Single file result
        const baseName = file.name.replace(/\.[pP][dD][fF]$/, '');
        downloadBlob(result.blob, `${baseName}_split.pdf`);
        addToast('PDF split successfully!', 'success');
      }
    } catch (error) {
      console.error('Split error:', error);
      addToast(
        error.response?.data?.message || error.message || 'Failed to split PDF',
        'error'
      );
    } finally {
      setLoading(false);
    }
  };

  const getUnassignedPages = () => {
    const assignedPages = new Set(pageGroups.flatMap(g => g.pages));
    return Array.from({ length: numPages || 0 }, (_, i) => i + 1).filter(p => !assignedPages.has(p));
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
          <Scissors size={28} />
          <h1>Split PDF</h1>
        </div>
        <p className="operation-description">
          Split a PDF into multiple documents. Choose individual pages or create custom groups.
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

          {file && numPages && (
            <>
              <div className="sidebar-section">
                <h3 className="sidebar-title">Split Mode</h3>
                <div className="split-mode-toggle">
                  <button
                    className={`mode-btn ${splitMode === 'individual' ? 'active' : ''}`}
                    onClick={() => setSplitMode('individual')}
                  >
                    Individual Pages
                  </button>
                  <button
                    className={`mode-btn ${splitMode === 'custom' ? 'active' : ''}`}
                    onClick={() => setSplitMode('custom')}
                  >
                    Custom Groups
                  </button>
                </div>
                <p className="mode-description">
                  {splitMode === 'individual' 
                    ? `Split into ${numPages} separate PDFs (one per page)` 
                    : 'Create custom groups of pages'}
                </p>
              </div>

              {splitMode === 'custom' && (
                <div className="sidebar-section">
                  <h3 className="sidebar-title">
                    Page Groups
                    <button className="add-group-btn" onClick={addGroup} title="Add Group">
                      <Plus size={16} />
                    </button>
                  </h3>
                  
                  {pageGroups.map((group, index) => (
                    <div key={group.id} className="page-group">
                      <div className="group-header">
                        <span className="group-label">Document {index + 1}</span>
                        {pageGroups.length > 1 && (
                          <button
                            className="remove-group-btn"
                            onClick={() => removeGroup(group.id)}
                            title="Remove Group"
                          >
                            <X size={14} />
                          </button>
                        )}
                      </div>
                      <div className="group-pages">
                        {group.pages.length > 0 ? (
                          group.pages.map(p => (
                            <span key={p} className="page-badge" onClick={() => togglePageInGroup(group.id, p)}>
                              {p} <X size={10} />
                            </span>
                          ))
                        ) : (
                          <span className="no-pages">Click pages below to add</span>
                        )}
                      </div>
                    </div>
                  ))}

                  {getUnassignedPages().length > 0 && (
                    <div className="unassigned-pages">
                      <span className="unassigned-label">Unassigned: </span>
                      {getUnassignedPages().map(p => (
                        <span key={p} className="unassigned-page">{p}</span>
                      ))}
                    </div>
                  )}
                </div>
              )}

              <div className="sidebar-actions">
                <Button
                  onClick={handleSplit}
                  loading={loading}
                  disabled={loading}
                  icon={<Download size={20} />}
                  fullWidth
                  size="lg"
                >
                  {loading ? 'Splitting...' : 'Split & Download'}
                </Button>
              </div>
            </>
          )}
        </aside>

        <main className="operation-preview">
          {file ? (
            <>
              <div className="preview-header">
                <h3>Preview: {file.name} {numPages && `(${numPages} pages)`}</h3>
              </div>
              <div className="split-page-grid">
                <Document
                  file={file}
                  onLoadSuccess={onDocumentLoadSuccess}
                  loading={<div className="loading">Loading PDF...</div>}
                >
                  {numPages && Array.from({ length: numPages }, (_, i) => i + 1).map((pageNum) => {
                    const groupIndex = pageGroups.findIndex(g => g.pages.includes(pageNum));
                    const groupColor = groupIndex >= 0 ? `hsl(${(groupIndex * 60) % 360}, 70%, 50%)` : 'transparent';
                    
                    return (
                      <div
                        key={pageNum}
                        className={`split-page-item ${splitMode === 'custom' ? 'selectable' : ''}`}
                        style={{ borderColor: splitMode === 'custom' ? groupColor : undefined }}
                        onClick={() => {
                          if (splitMode === 'custom' && pageGroups.length > 0) {
                            // Add to first group that doesn't have this page, or toggle in current group
                            const currentGroup = pageGroups.find(g => g.pages.includes(pageNum));
                            if (currentGroup) {
                              togglePageInGroup(currentGroup.id, pageNum);
                            } else {
                              togglePageInGroup(pageGroups[pageGroups.length - 1].id, pageNum);
                            }
                          }
                        }}
                      >
                        <Page
                          pageNumber={pageNum}
                          width={150}
                          renderTextLayer={false}
                          renderAnnotationLayer={false}
                        />
                        <div className="page-number">
                          Page {pageNum}
                          {splitMode === 'custom' && groupIndex >= 0 && (
                            <span className="group-indicator" style={{ backgroundColor: groupColor }}>
                              Doc {groupIndex + 1}
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </Document>
              </div>
            </>
          ) : (
            <div className="preview-empty">
              <Scissors size={64} />
              <h3>Upload a PDF to preview</h3>
              <p>Split into individual pages or create custom groups</p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default SplitPage;
