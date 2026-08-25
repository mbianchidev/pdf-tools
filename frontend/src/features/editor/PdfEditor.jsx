import { useMemo, useState } from 'react';
import { Document, Page } from 'react-pdf';
import '../../lib/pdfWorker';
import { normalizedRectangleStyle } from './coordinates';
import { pageRenderRotation } from './pageModel';
import PageThumbnailOrganizer from './PageThumbnailOrganizer';
import './PdfEditor.css';

const PdfEditor = ({
  file,
  pages,
  onPagesChange,
  overlays = [],
  organizerOptions = {},
}) => {
  const [selectedPageId, setSelectedPageId] = useState(null);
  const selectedPage = useMemo(
    () => pages.find((page) => page.id === selectedPageId) || pages[0],
    [pages, selectedPageId],
  );

  if (!file) {
    return <p className="pdf-editor-empty">Upload a PDF to start editing.</p>;
  }

  return (
    <div className="pdf-editor">
      <aside>
        <PageThumbnailOrganizer
          file={file}
          pages={pages}
          onPagesChange={onPagesChange}
          selectedPageId={selectedPage?.id ?? null}
          onSelectPage={setSelectedPageId}
          {...organizerOptions}
        />
      </aside>
      <main>
        {selectedPage && (
          <Document file={file} loading={<p>Loading PDF preview...</p>}>
            <div className="pdf-editor-page">
              <Page
                pageNumber={selectedPage.sourcePage}
                rotate={pageRenderRotation(selectedPage)}
                width={700}
              />
              {overlays
                .filter((overlay) => overlay.pageId === selectedPage.id)
                .map((overlay) => (
                  <div
                    key={overlay.id}
                    className={`pdf-editor-overlay ${overlay.type || ''}`}
                    style={normalizedRectangleStyle(overlay.rectangle)}
                    aria-label={overlay.label || 'PDF edit overlay'}
                  />
                ))}
            </div>
          </Document>
        )}
      </main>
    </div>
  );
};

export default PdfEditor;
