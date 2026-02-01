import React, { useState, useEffect } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import { ChevronLeft, ChevronRight, ZoomIn, ZoomOut, RotateCw } from 'lucide-react';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import './PdfViewer.css';

// Set worker source for PDF.js
pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

const PdfViewer = ({ 
  file, 
  onPageSelect, 
  selectable = false, 
  selectedPages = [],
  showThumbnails = true,
  compact = false
}) => {
  const [numPages, setNumPages] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [scale, setScale] = useState(1.0);
  const [rotation, setRotation] = useState(0);
  const [pdfUrl, setPdfUrl] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (file) {
      // Create object URL for the file
      const url = URL.createObjectURL(file);
      // Reset viewer state when new file is loaded
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPdfUrl(url);
      setLoading(true);
      setError(null);
      setCurrentPage(1);
      setScale(1.0);
      setRotation(0);

      return () => {
        URL.revokeObjectURL(url);
      };
    }
  }, [file]);

  const onDocumentLoadSuccess = ({ numPages }) => {
    setNumPages(numPages);
    setLoading(false);
  };

  const onDocumentLoadError = (err) => {
    console.error('Error loading PDF:', err);
    setError('Failed to load PDF. Please ensure the file is a valid PDF document.');
    setLoading(false);
  };

  const goToPrevPage = () => {
    setCurrentPage((prev) => Math.max(prev - 1, 1));
  };

  const goToNextPage = () => {
    setCurrentPage((prev) => Math.min(prev + 1, numPages || 1));
  };

  const zoomIn = () => {
    setScale((prev) => Math.min(prev + 0.25, 3.0));
  };

  const zoomOut = () => {
    setScale((prev) => Math.max(prev - 0.25, 0.5));
  };

  const rotate = () => {
    setRotation((prev) => (prev + 90) % 360);
  };

  const handlePageClick = (pageNum) => {
    if (selectable && onPageSelect) {
      onPageSelect(pageNum);
    } else {
      setCurrentPage(pageNum);
    }
  };

  const isPageSelected = (pageNum) => {
    return selectedPages.includes(pageNum);
  };

  if (!file) {
    return (
      <div className="pdf-viewer-empty">
        <p>No PDF file loaded</p>
      </div>
    );
  }

  return (
    <div className={`pdf-viewer ${compact ? 'pdf-viewer-compact' : ''}`}>
      {/* Toolbar */}
      <div className="pdf-viewer-toolbar">
        <div className="pdf-viewer-nav">
          <button 
            className="pdf-viewer-btn" 
            onClick={goToPrevPage} 
            disabled={currentPage <= 1}
            aria-label="Previous page"
          >
            <ChevronLeft size={20} />
          </button>
          <span className="pdf-viewer-page-info">
            {currentPage} / {numPages || '?'}
          </span>
          <button 
            className="pdf-viewer-btn" 
            onClick={goToNextPage} 
            disabled={currentPage >= (numPages || 1)}
            aria-label="Next page"
          >
            <ChevronRight size={20} />
          </button>
        </div>
        <div className="pdf-viewer-zoom">
          <button 
            className="pdf-viewer-btn" 
            onClick={zoomOut} 
            disabled={scale <= 0.5}
            aria-label="Zoom out"
          >
            <ZoomOut size={20} />
          </button>
          <span className="pdf-viewer-zoom-info">{Math.round(scale * 100)}%</span>
          <button 
            className="pdf-viewer-btn" 
            onClick={zoomIn} 
            disabled={scale >= 3.0}
            aria-label="Zoom in"
          >
            <ZoomIn size={20} />
          </button>
          <button 
            className="pdf-viewer-btn" 
            onClick={rotate}
            aria-label="Rotate"
          >
            <RotateCw size={20} />
          </button>
        </div>
      </div>

      <div className="pdf-viewer-content">
        {/* Thumbnails sidebar */}
        {showThumbnails && numPages && !compact && (
          <div className="pdf-viewer-thumbnails">
            <h4 className="thumbnails-title">Pages</h4>
            <div className="thumbnails-list">
              {Array.from({ length: numPages }, (_, i) => i + 1).map((pageNum) => (
                <div
                  key={pageNum}
                  className={`thumbnail-item ${currentPage === pageNum ? 'active' : ''} ${
                    isPageSelected(pageNum) ? 'selected' : ''
                  } ${selectable ? 'selectable' : ''}`}
                  onClick={() => handlePageClick(pageNum)}
                >
                  <Document file={pdfUrl} loading={null}>
                    <Page
                      pageNumber={pageNum}
                      width={100}
                      renderTextLayer={false}
                      renderAnnotationLayer={false}
                    />
                  </Document>
                  <span className="thumbnail-number">
                    {selectable && isPageSelected(pageNum) && (
                      <span className="selected-indicator">✓</span>
                    )}
                    {pageNum}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Main viewer */}
        <div className="pdf-viewer-main">
          {loading && (
            <div className="pdf-viewer-loading">
              <div className="loading-spinner"></div>
              <p>Loading PDF...</p>
            </div>
          )}
          
          {error && (
            <div className="pdf-viewer-error">
              <p>{error}</p>
            </div>
          )}

          {pdfUrl && !error && (
            <Document
              file={pdfUrl}
              onLoadSuccess={onDocumentLoadSuccess}
              onLoadError={onDocumentLoadError}
              loading={null}
            >
              <Page
                pageNumber={currentPage}
                scale={scale}
                rotate={rotation}
                renderTextLayer={true}
                renderAnnotationLayer={true}
              />
            </Document>
          )}
        </div>
      </div>
    </div>
  );
};

export default PdfViewer;
