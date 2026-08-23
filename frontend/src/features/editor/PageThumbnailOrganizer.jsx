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
}) => {
  const [dragIndex, setDragIndex] = useState(null);

  const initialize = async (document) => {
    if (pages.length === 0) {
      const intrinsicRotations = await Promise.all(
        Array.from({ length: document.numPages }, async (_, index) => {
          const page = await document.getPage(index + 1);
          return page.rotate ?? 0;
        }),
      );
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

  return (
    <Document
      file={file}
      onLoadSuccess={initialize}
      loading={<p>Loading page thumbnails...</p>}
    >
      <ol className="page-organizer" aria-label="Document pages">
        {pages.map((page, index) => (
          <li
            key={page.id}
            className={page.id === selectedPageId ? 'selected' : ''}
            draggable
            onDragStart={() => setDragIndex(index)}
            onDragOver={(event) => event.preventDefault()}
            onDrop={() => handleDrop(index)}
          >
            <button
              type="button"
              className="page-organizer-preview"
              onClick={() => onSelectPage?.(page.id)}
              aria-label={`Select page ${index + 1}`}
            >
              <GripVertical size={16} aria-hidden="true" />
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
              <button
                type="button"
                onClick={() => index > 0 && update(movePage(pages, index, index - 1))}
                disabled={index === 0}
                aria-label={`Move page ${index + 1} left`}
              >
                ←
              </button>
              <button
                type="button"
                onClick={() => index < pages.length - 1 && update(movePage(pages, index, index + 1))}
                disabled={index === pages.length - 1}
                aria-label={`Move page ${index + 1} right`}
              >
                →
              </button>
              {allowRotate && (
                <button
                  type="button"
                  onClick={() => update(rotatePage(pages, index), page.id)}
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
                  aria-label={`Duplicate page ${index + 1}`}
                >
                  <Copy size={15} />
                </button>
              )}
              {allowDelete && (
                <button
                  type="button"
                  onClick={() => update(removePage(pages, index))}
                  disabled={pages.length === 1}
                  aria-label={`Delete page ${index + 1}`}
                >
                  <Trash2 size={15} />
                </button>
              )}
            </div>
          </li>
        ))}
      </ol>
    </Document>
  );
};

export default PageThumbnailOrganizer;
