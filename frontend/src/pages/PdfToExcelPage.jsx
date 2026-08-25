import { useMemo, useState } from 'react';
import { Sheet } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const PdfToExcelPage = () => {
  const [sheetMode, setSheetMode] = useState('pages');
  const [includeNonTableText, setIncludeNonTableText] = useState(true);
  const tableOnly = sheetMode === 'tables';
  const options = useMemo(() => ({
    sheetMode,
    includeNonTableText,
  }), [includeNonTableText, sheetMode]);

  return (
    <OfficeToPdfPage
      operation="pdf-to-excel"
      title="PDF to Excel"
      description="Detect aligned tables and organize extracted PDF text into worksheets."
      Icon={Sheet}
      uploadTitle="Upload PDF"
      accept={PDF_ACCEPT}
      hint="One unencrypted PDF file"
      actionLabel="Convert PDF to Excel"
      runningLabel="Building workbook..."
      successMessage="Excel workbook download started!"
      previewEyebrow="PDFBox + Apache POI"
      previewTitle={tableOnly
        ? 'Create one sheet per detected table'
        : 'Keep each PDF page on its own sheet'}
      previewDescription={tableOnly
        ? 'Aligned text rows and columns become typed Excel cells in separate table worksheets.'
        : 'Detected tables and optional non-table text stay in page order on Page 1, Page 2, and later worksheets.'}
      fidelityWarning="Table detection is heuristic. Merged cells, ruled layouts, complex columns, charts, images, and scanned text can require manual correction."
      securityTitle="Bounded table extraction"
      securityDescription="PDFBox and Apache POI run in a killable Java worker with page, text, table, row, column, cell, output, heap, and wall-time limits."
      options={options}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Workbook settings</h3>
          <label>
            Worksheet layout
            <select
              aria-label="Worksheet layout"
              value={sheetMode}
              onChange={(event) => setSheetMode(event.target.value)}
              disabled={running}
            >
              <option value="pages">One sheet per PDF page</option>
              <option value="tables">One sheet per detected table</option>
            </select>
          </label>
          {!tableOnly && (
            <label className="conversion-toggle">
              <input
                type="checkbox"
                checked={includeNonTableText}
                onChange={(event) => setIncludeNonTableText(
                  event.target.checked
                )}
                disabled={running}
              />
              Include text outside tables
            </label>
          )}
        </div>
      )}
    />
  );
};

export default PdfToExcelPage;
