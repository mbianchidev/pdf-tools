import { useState } from 'react';
import { Copy, GripVertical, RotateCw, Trash2 } from 'lucide-react';
import { Document, Page } from 'react-pdf';
import '../../lib/pdfWorker';
import {
  createPageModel,
  duplicatePage,
  movePage,
  pageRenderRotation,
  removePage,
  rotatePage,
} from './pageModel';
import './PdfEditor.css';

const PageThumbnailOrganizer = ({
  file,
  pages,
  onPagesChange,
  selectedPageId,
  onSelectPage,
  allowDuplicate = true,
  allowDelete = true,
  allowRotate = true,
  allowMove = true,
  disabled = false,
  maxPages = 1000,
  onError,
}) => {
  const [dragIndex, setDragIndex] = useState(null);
  const [pageWindow, setPageWindow] = useState(0);
  const pageSize = 100;

  const initialize = async (document) => {
    if (pages.length === 0) {
      if (document.numPages > maxPages) {
        onError?.(`PDF exceeds the ${maxPages}-page organizer limit.`);
        return;
      }
      setPageWindow(0);
      const intrinsicRotations = [];
      for (let start = 0; start < document.numPages; start += 25) {
        const rotations = await Promise.all(
          Array.from(
            { length: Math.min(25, document.numPages - start) },
            async (_, offset) => {
              const page = await document.getPage(start + offset + 1);
              return page.rotate ?? 0;
            },
          ),
        );
        intrinsicRotations.push(...rotations);
      }
      const initialPages = createPageModel(
        document.numPages,
        undefined,
        intrinsicRotations,
      );
      onPagesChange(initialPages);
      onSelectPage?.(initialPages[0].id);
    }
  };

  const update = (nextPages, selectedId = selectedPageId) => {
    const lastWindow = Math.max(
      Math.ceil(nextPages.length / pageSize) - 1,
      0,
    );
    setPageWindow((current) => Math.min(current, lastWindow));
    onPagesChange(nextPages);
    if (selectedId && nextPages.some((page) => page.id === selectedId)) {
      onSelectPage?.(selectedId);
    } else {
      onSelectPage?.(nextPages[0]?.id ?? null);
    }
  };

  const handleDrop = (targetIndex) => {
    if (dragIndex !== null && dragIndex !== targetIndex) {
      update(movePage(pages, dragIndex, targetIndex));
    }
    setDragIndex(null);
  };

  const pageWindowCount = Math.max(Math.ceil(pages.length / pageSize), 1);
  const visibleWindow = Math.min(pageWindow, pageWindowCount - 1);
  const pageStart = visibleWindow * pageSize;
  const visiblePages = pages.slice(pageStart, pageStart + pageSize);

  return (
    <Document
      file={file}
      onLoadSuccess={initialize}
      loading={<p>Loading page thumbnails...</p>}
    >
      {pages.length > pageSize && (
        <nav className="page-organizer-pagination" aria-label="Page groups">
          <button
            type="button"
            onClick={() => setPageWindow(Math.max(visibleWindow - 1, 0))}
            disabled={disabled || visibleWindow === 0}
          >
            Previous pages
          </button>
          <span>
            Pages {pageStart + 1}-
            {Math.min(pageStart + pageSize, pages.length)} of {pages.length}
          </span>
          <button
            type="button"
            onClick={() => setPageWindow(Math.min(
              visibleWindow + 1,
              pageWindowCount - 1,
            ))}
            disabled={disabled || visibleWindow >= pageWindowCount - 1}
          >
            Next pages
          </button>
        </nav>
      )}
      <ol className="page-organizer" aria-label="Document pages">
        {visiblePages.map((page, visibleIndex) => {
          const index = pageStart + visibleIndex;
          return (
          <li
            key={page.id}
            className={page.id === selectedPageId ? 'selected' : ''}
            draggable={allowMove && !disabled}
            onDragStart={() => allowMove
              && !disabled
              && setDragIndex(index)}
            onDragOver={(event) => allowMove
              && !disabled
              && event.preventDefault()}
            onDrop={() => allowMove && !disabled && handleDrop(index)}
          >
            <button
              type="button"
              className="page-organizer-preview"
              onClick={() => onSelectPage?.(page.id)}
              aria-label={`Select page ${index + 1}`}
              disabled={disabled}
            >
              {allowMove && <GripVertical size={16} aria-hidden="true" />}
              <Page
                pageNumber={page.sourcePage}
                rotate={pageRenderRotation(page)}
                width={120}
                renderTextLayer={false}
                renderAnnotationLayer={false}
              />
              <span>Page {index + 1}</span>
            </button>
            <div className="page-organizer-actions">
              {allowMove && (
                <>
                  <button
                    type="button"
                    onClick={() => index > 0
                      && update(movePage(pages, index, index - 1))}
                    disabled={disabled || index === 0}
                    aria-label={`Move page ${index + 1} left`}
                  >
                    ←
                  </button>
                  <button
                    type="button"
                    onClick={() => index < pages.length - 1
                      && update(movePage(pages, index, index + 1))}
                    disabled={disabled || index === pages.length - 1}
                    aria-label={`Move page ${index + 1} right`}
                  >
                    →
                  </button>
                </>
              )}
              {allowRotate && (
                <button
                  type="button"
                  onClick={() => update(rotatePage(pages, index), page.id)}
                  disabled={disabled}
                  aria-label={`Rotate page ${index + 1}`}
                >
                  <RotateCw size={15} />
                </button>
              )}
              {allowDuplicate && (
                <button
                  type="button"
                  onClick={() => {
                    const nextPages = duplicatePage(pages, index);
                    update(nextPages, nextPages[index + 1].id);
                  }}
                  disabled={disabled || pages.length >= maxPages}
                  aria-label={`Duplicate page ${index + 1}`}
                >
                  <Copy size={15} />
                </button>
              )}
              {allowDelete && (
                <button
                  type="button"
                  onClick={() => update(removePage(pages, index))}
                  disabled={disabled || pages.length === 1}
                  aria-label={`Delete page ${index + 1}`}
                >
                  <Trash2 size={15} />
                </button>
              )}
            </div>
          </li>
          );
        })}
      </ol>
    </Document>
  );
};

export default PageThumbnailOrganizer;
