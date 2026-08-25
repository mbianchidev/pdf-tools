import {
  useEffect,
  useState,
} from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  FileJson,
  Wrench,
} from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
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
  const [state, setState] = useState({
    outputId: null,
    report: null,
    error: '',
  });
  const reportOutput = job?.status === 'COMPLETED'
    ? job.outputs?.find((output) => (
      output.mediaType === 'application/json'
    ))
    : null;

  useEffect(() => {
    if (!reportOutput) {
      return undefined;
    }
    let active = true;
    const load = async () => {
      try {
        const blob = await jobService.download(reportOutput);
        const report = JSON.parse(await blob.text());
        if (!['repaired', 'partially-recovered'].includes(report.status)
            || !Number.isInteger(report.recoveredPages)
            || !Array.isArray(report.warnings)) {
          throw new Error('The repair report is invalid.');
        }
        if (active) {
          setState({
            outputId: reportOutput.id,
            report,
            error: '',
          });
        }
      } catch (error) {
        console.error('Repair report error:', error);
        if (active) {
          setState({
            outputId: reportOutput.id,
            report: null,
            error: error.message || 'The repair report could not be read.',
          });
        }
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [reportOutput]);

  if (!reportOutput) {
    return null;
  }
  if (state.outputId !== reportOutput.id) {
    return (
      <div className="repair-result repair-result--loading" aria-live="polite">
        Reading repair report...
      </div>
    );
  }
  if (state.error) {
    return (
      <div className="repair-result repair-result--warning" role="alert">
        <AlertTriangle size={20} />
        <div>
          <strong>Repair report unavailable</strong>
          <p>{state.error}</p>
        </div>
      </div>
    );
  }
  if (!state.report) {
    return null;
  }

  const partial = state.report.status === 'partially-recovered';
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
        <p>{state.report.summary}</p>
        <span>
          {state.report.recoveredPages}{' '}
          {state.report.recoveredPages === 1 ? 'page' : 'pages'} recovered
        </span>
        {state.report.warnings.length > 0 && (
          <ul>
            {state.report.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        )}
        <button
          className="repair-report-download"
          type="button"
          onClick={() => startBrowserDownload(
            jobService.getDownloadUrl(reportOutput),
            reportOutput.filename,
          )}
        >
          <FileJson size={16} />
          Download repair report
        </button>
      </div>
    </div>
  );
};

export default RepairPage;
