import { useMemo, useState } from 'react';
import {
  Archive,
  BadgeCheck,
  FileJson,
} from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { useJobJsonReport } from '../features/jobs/useJobJsonReport';
import { jobService } from '../services/jobService';
import '../features/office/IsolatedConversionControls.css';
import './PdfAPage.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const PROFILES = {
  'pdfa-1b': {
    label: 'PDF/A-1b',
    description: 'PDF 1.4 baseline for broad archival compatibility.',
  },
  'pdfa-2b': {
    label: 'PDF/A-2b',
    description: 'Recommended modern baseline with PDF 1.7 features.',
  },
  'pdfa-3b': {
    label: 'PDF/A-3b',
    description: 'PDF/A-2 capabilities plus embedded-file support.',
  },
};

const PdfAPage = () => {
  const [profile, setProfile] = useState('pdfa-2b');
  const options = useMemo(() => ({ profile }), [profile]);
  const selected = PROFILES[profile];

  return (
    <OfficeToPdfPage
      operation="pdf-to-pdfa"
      title="PDF to PDF/A"
      description="Create an archival PDF and publish it only after independent conformance validation."
      Icon={Archive}
      uploadTitle="Upload PDF"
      accept={PDF_ACCEPT}
      hint="One unencrypted PDF file"
      actionLabel="Convert to PDF/A"
      runningLabel="Converting and validating..."
      successMessage="Validated PDF/A download started!"
      previewEyebrow={`${selected.label} archival export`}
      previewTitle={selected.description}
      previewDescription="LibreOffice Draw performs the conversion in the networkless Office sidecar. A separate veraPDF process validates the exact requested profile before publication."
      fidelityWarning="LibreOffice Draw reimports the PDF before archival export, so fonts, forms, annotations, links, transparency, and layout can change. Conformance does not guarantee visual parity."
      securityTitle="Two isolated validation boundaries"
      securityDescription="LibreOffice converts without network access under native resource limits. An isolated veraPDF worker then validates the result with separate heap and wall-time limits."
      options={options}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Archival settings</h3>
          <label>
            PDF/A profile
            <select
              aria-label="PDF/A profile"
              value={profile}
              onChange={(event) => setProfile(event.target.value)}
              disabled={running}
            >
              {Object.entries(PROFILES).map(([value, entry]) => (
                <option value={value} key={value}>
                  {entry.label}
                </option>
              ))}
            </select>
          </label>
          <div className="pdfa-profile-guide">
            {Object.entries(PROFILES).map(([value, entry]) => (
              <div
                className={value === profile ? 'is-active' : ''}
                key={value}
              >
                <strong>{entry.label}</strong>
                <span>{entry.description}</span>
              </div>
            ))}
          </div>
        </div>
      )}
      renderResult={({ job }) => <PdfAResult job={job} />}
    />
  );
};

const PdfAResult = ({ job }) => {
  const {
    output,
    loading,
    report,
    error,
  } = useJobJsonReport(job, validatePdfAReport, 'veraPDF validation');

  if (!output) {
    return null;
  }
  if (loading) {
    return (
      <div className="pdfa-result pdfa-result--loading" aria-live="polite">
        Reading veraPDF report...
      </div>
    );
  }
  if (error || !report) {
    return (
      <div className="pdfa-result pdfa-result--error" role="alert">
        <strong>Validation report unavailable</strong>
        <p>{error || 'The validation report could not be read.'}</p>
      </div>
    );
  }

  return (
    <div className="pdfa-result pdfa-result--success" aria-live="polite">
      <BadgeCheck size={22} />
      <div>
        <strong>veraPDF compliant</strong>
        <p>{PROFILES[report.profile]?.label || report.profile}</p>
        <span>{report.totalAssertions} assertions checked</span>
        <button
          className="pdfa-report-download"
          type="button"
          onClick={() => startBrowserDownload(
            jobService.getDownloadUrl(output),
            output.filename,
          )}
        >
          <FileJson size={16} />
          Download validation report
        </button>
      </div>
    </div>
  );
};

const validatePdfAReport = (report) => {
  if (report.status !== 'compliant'
      || !PROFILES[report.profile]
      || report.compliant !== true
      || !Number.isInteger(report.totalAssertions)
      || report.totalAssertions < 1
      || report.failedChecks !== 0
      || !Array.isArray(report.failures)
      || report.failures.length !== 0) {
    throw new Error('The veraPDF validation report is invalid.');
  }
};

export default PdfAPage;
