import { useMemo, useState } from 'react';
import { CodeXml } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';

const HTML_ACCEPT = {
  'text/html': ['.html', '.htm'],
  'application/xhtml+xml': ['.html', '.htm'],
};

const HtmlToPdfPage = () => {
  const [pageSize, setPageSize] = useState('a4');
  const [orientation, setOrientation] = useState('portrait');
  const [printBackground, setPrintBackground] = useState(true);
  const [marginMm, setMarginMm] = useState('10');
  const parsedMargin = Number(marginMm);
  const validationError = !Number.isInteger(parsedMargin)
    || parsedMargin < 0
    || parsedMargin > 50
    ? 'Margin must be a whole number from 0 to 50 mm.'
    : null;
  const options = useMemo(() => ({
    pageSize,
    orientation,
    printBackground,
    marginMm: parsedMargin,
  }), [orientation, pageSize, parsedMargin, printBackground]);

  return (
    <OfficeToPdfPage
      operation="html-to-pdf"
      title="HTML to PDF"
      description="Render one self-contained HTML document with isolated Chromium."
      Icon={CodeXml}
      uploadTitle="Upload HTML"
      accept={HTML_ACCEPT}
      hint="UTF-8 HTML or HTM file"
      actionLabel="Convert HTML to PDF"
      runningLabel="Rendering HTML..."
      successMessage="Rendered HTML download started!"
      previewEyebrow="Playwright + Chromium"
      previewTitle="Print a self-contained web page"
      previewDescription="Inline styles, scripts, SVG, and data-URL assets render with explicit paper controls."
      fidelityWarning="External URLs, local files, frames, workers, and network requests are blocked. Bundle required assets inline."
      securityTitle="Browser sandbox"
      securityDescription="Chromium runs as a non-root user in a networkless, read-only sidecar with its own seccomp profile, tmpfs scratch, and bounded resources."
      options={options}
      validationError={validationError}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Print settings</h3>
          <label>
            Paper size
            <select
              aria-label="Paper size"
              value={pageSize}
              onChange={(event) => setPageSize(event.target.value)}
              disabled={running}
            >
              <option value="a4">A4</option>
              <option value="letter">Letter</option>
              <option value="legal">Legal</option>
            </select>
          </label>
          <label>
            Orientation
            <select
              aria-label="Page orientation"
              value={orientation}
              onChange={(event) => setOrientation(event.target.value)}
              disabled={running}
            >
              <option value="portrait">Portrait</option>
              <option value="landscape">Landscape</option>
            </select>
          </label>
          <label>
            Margin (mm)
            <input
              aria-label="Page margin"
              type="number"
              min="0"
              max="50"
              step="1"
              value={marginMm}
              onChange={(event) => setMarginMm(event.target.value)}
              disabled={running}
            />
          </label>
          <label className="conversion-toggle">
            <input
              type="checkbox"
              checked={printBackground}
              onChange={(event) => setPrintBackground(event.target.checked)}
              disabled={running}
            />
            Print CSS backgrounds
          </label>
          {validationError && (
            <p className="conversion-error" role="alert">
              {validationError}
            </p>
          )}
        </div>
      )}
    />
  );
};

export default HtmlToPdfPage;
