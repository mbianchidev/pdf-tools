import { useMemo, useState } from 'react';
import { FileText } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const PdfToMarkdownPage = () => {
  const [detectHeadings, setDetectHeadings] = useState(true);
  const [detectLists, setDetectLists] = useState(true);
  const [detectTables, setDetectTables] = useState(true);
  const [includeImages, setIncludeImages] = useState(true);
  const [preservePageBreaks, setPreservePageBreaks] = useState(true);
  const options = useMemo(() => ({
    detectHeadings,
    detectLists,
    detectTables,
    includeImages,
    preservePageBreaks,
  }), [
    detectHeadings,
    detectLists,
    detectTables,
    includeImages,
    preservePageBreaks,
  ]);

  return (
    <OfficeToPdfPage
      operation="pdf-to-markdown"
      title="PDF to Markdown"
      description="Recover reading order and common document structure as portable Markdown."
      Icon={FileText}
      uploadTitle="Upload PDF"
      accept={PDF_ACCEPT}
      hint="One text-based, unencrypted PDF file"
      actionLabel="Convert PDF to Markdown"
      runningLabel="Building Markdown bundle..."
      successMessage="Markdown bundle download started!"
      previewEyebrow="PDFBox structured extraction"
      previewTitle="Markdown plus linked images in one ZIP"
      previewDescription="The bundle contains document.md and, when selected, extracted PNG images under images/ with relative links ready to use."
      fidelityWarning="Heading, list, table, and reading-order recovery is heuristic. Image-only and scanned PDFs are rejected explicitly; run OCR before converting them."
      securityTitle="Bounded local extraction"
      securityDescription="PDFBox runs in a killable Java worker with page-tree, text, table, image, pixel, output, heap, and wall-time limits."
      options={options}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Structure settings</h3>
          <Toggle
            label="Detect headings"
            checked={detectHeadings}
            onChange={setDetectHeadings}
            disabled={running}
          />
          <Toggle
            label="Detect lists"
            checked={detectLists}
            onChange={setDetectLists}
            disabled={running}
          />
          <Toggle
            label="Detect tables"
            checked={detectTables}
            onChange={setDetectTables}
            disabled={running}
          />
          <Toggle
            label="Include extracted images"
            checked={includeImages}
            onChange={setIncludeImages}
            disabled={running}
          />
          <Toggle
            label="Preserve page breaks"
            checked={preservePageBreaks}
            onChange={setPreservePageBreaks}
            disabled={running}
          />
        </div>
      )}
    />
  );
};

const Toggle = ({
  label,
  checked,
  onChange,
  disabled,
}) => (
  <label className="conversion-toggle">
    <input
      type="checkbox"
      checked={checked}
      onChange={(event) => onChange(event.target.checked)}
      disabled={disabled}
    />
    {label}
  </label>
);

export default PdfToMarkdownPage;
