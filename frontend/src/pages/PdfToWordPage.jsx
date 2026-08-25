import { useMemo, useState } from 'react';
import { FileType } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const PdfToWordPage = () => {
  const [mode, setMode] = useState('editable');
  const [includeImages, setIncludeImages] = useState(true);
  const [detectTables, setDetectTables] = useState(true);
  const [preservePageBreaks, setPreservePageBreaks] = useState(true);
  const options = useMemo(() => ({
    mode,
    includeImages,
    detectTables,
    preservePageBreaks,
  }), [detectTables, includeImages, mode, preservePageBreaks]);
  const visual = mode === 'visual';

  return (
    <OfficeToPdfPage
      operation="pdf-to-word"
      title="PDF to Word"
      description="Create an editable best-effort DOCX or preserve each page visually."
      Icon={FileType}
      uploadTitle="Upload PDF"
      accept={PDF_ACCEPT}
      hint="One unencrypted PDF file"
      actionLabel="Convert PDF to Word"
      runningLabel="Building Word document..."
      successMessage="Word document download started!"
      previewEyebrow="PDFBox + Apache POI"
      previewTitle={visual
        ? 'Preserve each page as an image'
        : 'Recover editable document structure'}
      previewDescription={visual
        ? 'Each PDF page becomes a full-page image with matching pagination.'
        : 'Positioned text, aligned table rows, embedded images, headings, and page breaks become editable Word content.'}
      fidelityWarning={visual
        ? 'Visual mode preserves appearance but page contents are not editable or searchable in Word.'
        : 'Editable mode uses document heuristics. Complex columns, vector art, forms, equations, and scanned text can require manual correction.'}
      securityTitle="Bounded extraction"
      securityDescription="PDFBox and Apache POI run in a killable Java worker with page, text, image, pixel, output, heap, and wall-time limits."
      options={options}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Conversion mode</h3>
          <label>
            Output structure
            <select
              aria-label="Conversion mode"
              value={mode}
              onChange={(event) => setMode(event.target.value)}
              disabled={running}
            >
              <option value="editable">Editable best effort</option>
              <option value="visual">Visual page images</option>
            </select>
          </label>
          {!visual && (
            <>
              <label className="conversion-toggle">
                <input
                  type="checkbox"
                  checked={includeImages}
                  onChange={(event) => setIncludeImages(
                    event.target.checked
                  )}
                  disabled={running}
                />
                Include embedded images
              </label>
              <label className="conversion-toggle">
                <input
                  type="checkbox"
                  checked={detectTables}
                  onChange={(event) => setDetectTables(
                    event.target.checked
                  )}
                  disabled={running}
                />
                Detect aligned tables
              </label>
            </>
          )}
          <label className="conversion-toggle">
            <input
              type="checkbox"
              checked={preservePageBreaks}
              onChange={(event) => setPreservePageBreaks(
                event.target.checked
              )}
              disabled={running}
            />
            Preserve PDF page breaks
          </label>
        </div>
      )}
    />
  );
};

export default PdfToWordPage;
