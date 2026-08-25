import {
  AlertTriangle,
  CheckCircle2,
  FileJson,
  Wrench,
} from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { useJobJsonReport } from '../features/jobs/useJobJsonReport';
import { jobService } from '../services/jobService';
import './RepairPage.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const RepairPage = () => (
  <OfficeToPdfPage
    operation="repair"
    title="Repair PDF"
    description="Recover readable PDF structure with qpdf and report every warning."
    Icon={Wrench}
    uploadTitle="Upload damaged PDF"
    accept={PDF_ACCEPT}
    hint="One unencrypted PDF file"
    actionLabel="Repair PDF"
    runningLabel="Repairing PDF..."
    successMessage="Repaired PDF download started!"
    previewEyebrow="qpdf structural recovery"
    previewTitle="Rebuild cross-references and rewrite the file"
    previewDescription="qpdf reconstructs recoverable structure, rewrites the PDF deterministically, then validates the result before it becomes downloadable."
    fidelityWarning="Repair can recover damaged structure, but it cannot recreate missing page content, fonts, images, or objects. Always review a partially recovered document."
    securityTitle="Isolated native repair"
    securityDescription="qpdf runs as a non-root, network-denied process with OS resource, output, descriptor, diagnostic, and wall-time limits. Every result always includes a JSON repair report."
    renderResult={({ job }) => <RepairResult job={job} />}
  />
);

const RepairResult = ({ job }) => {
  const {
    output,
    loading,
    report,
    error,
  } = useJobJsonReport(job, validateRepairReport, 'Repair');

  if (!output) {
    return null;
  }
  if (loading) {
    return (
      <div className="repair-result repair-result--loading" aria-live="polite">
        Reading repair report...
      </div>
    );
  }
  if (error) {
    return (
      <div className="repair-result repair-result--warning" role="alert">
        <AlertTriangle size={20} />
        <div>
          <strong>Repair report unavailable</strong>
          <p>{error}</p>
        </div>
      </div>
    );
  }
  if (!report) {
    return null;
  }

  const partial = report.status === 'partially-recovered';
  const Icon = partial ? AlertTriangle : CheckCircle2;
  return (
    <div
      className={`repair-result ${
        partial ? 'repair-result--warning' : 'repair-result--success'
      }`}
      aria-live="polite"
    >
      <Icon size={22} />
      <div>
        <strong>
          {partial ? 'Partially recovered' : 'Structure repaired'}
        </strong>
        <p>{report.summary}</p>
        <span>
          {report.recoveredPages}{' '}
          {report.recoveredPages === 1 ? 'page' : 'pages'} recovered
        </span>
        {report.warnings.length > 0 && (
          <ul>
            {report.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        )}
        <button
          className="repair-report-download"
          type="button"
          onClick={() => startBrowserDownload(
            jobService.getDownloadUrl(output),
            output.filename,
          )}
        >
          <FileJson size={16} />
          Download repair report
        </button>
      </div>
    </div>
  );
};

const validateRepairReport = (report) => {
  if (!['repaired', 'partially-recovered'].includes(report.status)
      || !Number.isInteger(report.recoveredPages)
      || !Array.isArray(report.warnings)) {
    throw new Error('The repair report is invalid.');
  }
};

export default RepairPage;
