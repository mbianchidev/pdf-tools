import { useMemo, useState } from 'react';
import { Sheet } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';

const EXCEL_ACCEPT = {
  'application/vnd.ms-excel': ['.xls'],
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': [
    '.xlsx',
  ],
};

const ExcelToPdfPage = () => {
  const [printAreaMode, setPrintAreaMode] = useState('existing');
  const [printArea, setPrintArea] = useState('A1:F40');
  const [orientation, setOrientation] = useState('preserve');
  const validationError = printAreaMode === 'custom'
    && !/^\$?[A-Za-z]{1,3}\$?[1-9]\d*:\$?[A-Za-z]{1,3}\$?[1-9]\d*$/.test(
      printArea.trim(),
    )
    ? 'Enter one contiguous A1 range, such as A1:F40.'
    : null;
  const options = useMemo(() => ({
    printAreaMode,
    orientation,
    ...(printAreaMode === 'custom'
      ? { printArea: printArea.trim() }
      : {}),
  }), [orientation, printArea, printAreaMode]);

  return (
    <OfficeToPdfPage
      operation="excel-to-pdf"
      title="Excel to PDF"
      description="Convert XLSX and XLS workbooks with explicit print areas and orientation."
      Icon={Sheet}
      uploadTitle="Upload workbook"
      accept={EXCEL_ACCEPT}
      hint="XLSX or XLS file"
      actionLabel="Convert Excel to PDF"
      runningLabel="Converting Excel..."
      successMessage="Converted workbook download started!"
      previewEyebrow="LibreOffice Calc"
      previewTitle="Control printed workbook pages"
      previewDescription="Convert visible worksheets while preserving cell values, formulas, formatting, charts, and existing print settings."
      fidelityWarning="Unavailable fonts, external data, unsupported formulas, and Excel-specific layout behavior can change the PDF."
      options={options}
      validationError={validationError}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Print settings</h3>
          <label>
            Print area
            <select
              aria-label="Print area mode"
              value={printAreaMode}
              onChange={(event) => setPrintAreaMode(event.target.value)}
              disabled={running}
            >
              <option value="existing">Keep workbook settings</option>
              <option value="used">Use populated cells</option>
              <option value="custom">Custom range on every sheet</option>
            </select>
          </label>
          {printAreaMode === 'custom' && (
            <label>
              A1 range
              <input
                aria-label="Custom print area"
                value={printArea}
                onChange={(event) => setPrintArea(event.target.value)}
                placeholder="A1:F40"
                disabled={running}
              />
            </label>
          )}
          <label>
            Orientation
            <select
              aria-label="Page orientation"
              value={orientation}
              onChange={(event) => setOrientation(event.target.value)}
              disabled={running}
            >
              <option value="preserve">Keep workbook settings</option>
              <option value="portrait">Portrait</option>
              <option value="landscape">Landscape</option>
            </select>
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

export default ExcelToPdfPage;
