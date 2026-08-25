import { useMemo, useState } from 'react';
import { Presentation } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const PdfToPowerPointPage = () => {
  const [mode, setMode] = useState('editable');
  const [slideSize, setSlideSize] = useState('source');
  const [includeImages, setIncludeImages] = useState(true);
  const [detectTables, setDetectTables] = useState(true);
  const visual = mode === 'visual';
  const options = useMemo(() => ({
    mode,
    slideSize,
    includeImages,
    detectTables,
  }), [detectTables, includeImages, mode, slideSize]);

  return (
    <OfficeToPdfPage
      operation="pdf-to-powerpoint"
      title="PDF to PowerPoint"
      description="Turn each PDF page into one editable or visual slide."
      Icon={Presentation}
      uploadTitle="Upload PDF"
      accept={PDF_ACCEPT}
      hint="One unencrypted PDF file"
      actionLabel="Convert PDF to PowerPoint"
      runningLabel="Building presentation..."
      successMessage="PowerPoint download started!"
      previewEyebrow="PDFBox + Apache POI"
      previewTitle={visual
        ? 'Preserve each page as a slide image'
        : 'Recover editable slide elements'}
      previewDescription={visual
        ? 'Each source page is fitted onto one slide as a high-resolution image.'
        : 'Positioned text boxes, aligned tables, and raster images become editable slide elements where supported.'}
      fidelityWarning={visual
        ? 'Visual mode preserves appearance but the slide contents are not editable.'
        : 'Editable mode is best effort. Rotated pages use a visual fallback; vector art, clipping, equations, complex columns, forms, and annotations can require manual correction.'}
      securityTitle="Bounded slide extraction"
      securityDescription="PDFBox and Apache POI run in a killable Java worker with slide, text, image, pixel, output, heap, and wall-time limits."
      options={options}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Slide settings</h3>
          <label>
            Conversion mode
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
          <label>
            Slide size
            <select
              aria-label="Slide size"
              value={slideSize}
              onChange={(event) => setSlideSize(event.target.value)}
              disabled={running}
            >
              <option value="source">Match first PDF page</option>
              <option value="widescreen">Widescreen 16:9</option>
              <option value="standard">Standard 4:3</option>
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
                Include raster images
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
        </div>
      )}
    />
  );
};

export default PdfToPowerPointPage;
