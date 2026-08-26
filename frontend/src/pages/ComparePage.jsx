import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  ArrowLeft,
  Download,
  FileJson,
  GitCompareArrows,
} from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import FileUpload from '../components/FileUpload';
import ToastContainer from '../components/Toast';
import JobProgress from '../features/jobs/JobProgress';
import { startBrowserDownload } from '../features/jobs/startBrowserDownload';
import { useJobJsonReport } from '../features/jobs/useJobJsonReport';
import { usePdfJob } from '../features/jobs/usePdfJob';
import { useWorkspaceFile } from '../features/navigation/useWorkspaceFile';
import { getApiErrorMessage, jobService } from '../services/jobService';
import './OperationPage.css';
import './ComparePage.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const ComparePage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const workspaceFile = useWorkspaceFile();
  const [baseline, setBaseline] = useState(null);
  const [candidate, setCandidate] = useState(null);
  const [renderDpi, setRenderDpi] = useState(120);
  const [pixelTolerance, setPixelTolerance] = useState(12);
  const [layoutTolerancePoints, setLayoutTolerancePoints] = useState(2);
  const [toasts, setToasts] = useState([]);
  const handledJobRef = useRef(null);
  const {
    job,
    running,
    connectionError,
    start,
    cancel,
    reset,
  } = usePdfJob();
  const options = useMemo(() => ({
    renderDpi,
    pixelTolerance,
    layoutTolerancePoints,
  }), [layoutTolerancePoints, pixelTolerance, renderDpi]);

  const addToast = useCallback((
    message,
    type = 'success',
    duration = 5000,
  ) => {
    setToasts((current) => [
      ...current,
      { id: Date.now(), message, type, duration },
    ]);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const downloadOutput = useCallback((output) => {
    try {
      startBrowserDownload(
        jobService.getDownloadUrl(output),
        output.filename,
      );
      addToast('Comparison archive download started!', 'success');
    } catch (error) {
      console.error('Comparison download error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to download comparison'),
        'error',
      );
    }
  }, [addToast]);

  useEffect(() => {
    if (!job || !['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status)) {
      return undefined;
    }
    const resultKey = `${job.id}:${job.status}`;
    if (handledJobRef.current === resultKey) {
      return undefined;
    }
    handledJobRef.current = resultKey;
    let active = true;
    const handleResult = async () => {
      await Promise.resolve();
      if (!active) return;
      if (job.status === 'FAILED') {
        addToast(job.errorMessage || 'Failed to compare PDFs', 'error');
        return;
      }
      if (job.status === 'CANCELLED') {
        addToast('PDF comparison cancelled', 'error');
        return;
      }
      const archive = job.outputs.find(
        (output) => output.mediaType === 'application/zip',
      );
      if (!archive) {
        addToast('The comparison completed without an archive.', 'error');
        return;
      }
      downloadOutput(archive);
    };
    handleResult();
    return () => {
      active = false;
    };
  }, [addToast, downloadOutput, job]);

  const replaceFile = useCallback((role, files) => {
    if (running) return;
    const nextFile = files[0] || null;
    if (role === 'baseline') {
      setBaseline(nextFile);
      workspaceFile?.rememberPdfFile(
        nextFile || candidate,
        location.key,
      );
    } else {
      setCandidate(nextFile);
      workspaceFile?.rememberPdfFile(
        baseline || nextFile,
        location.key,
      );
    }
    handledJobRef.current = null;
    reset();
  }, [
    baseline,
    candidate,
    location.key,
    reset,
    running,
    workspaceFile,
  ]);

  const handleSubmit = async () => {
    if (!baseline || !candidate) {
      addToast('Upload both the baseline and candidate PDFs.', 'error');
      return;
    }
    try {
      await start('compare', [baseline, candidate], options);
    } catch (error) {
      console.error('Comparison job error:', error);
      addToast(
        getApiErrorMessage(error, 'Failed to start PDF comparison'),
        'error',
      );
    }
  };

  const handleCancel = async () => {
    try {
      await cancel();
    } catch (error) {
      console.error('Comparison cancellation error:', error);
      addToast(getApiErrorMessage(error, 'Failed to cancel job'), 'error');
    }
  };

  return (
    <div className="operation-page compare-page">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <header className="operation-header">
        <button className="back-button" onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>
        <div className="operation-title">
          <GitCompareArrows size={28} />
          <h1>Compare PDFs</h1>
        </div>
        <p className="operation-description">
          Compare text edits, moved layout, and rendered pixels page by page.
        </p>
      </header>

      <div className="operation-content">
        <aside className="operation-sidebar">
          <div className="sidebar-section">
            <h3 className="sidebar-title">1. Baseline PDF</h3>
            <FileUpload
              onFilesChange={(files) => replaceFile('baseline', files)}
              files={baseline ? [baseline] : []}
              accept={PDF_ACCEPT}
              multiple={false}
              disabled={running}
              hint="Original document"
            />
          </div>
          <div className="sidebar-section">
            <h3 className="sidebar-title">2. Candidate PDF</h3>
            <FileUpload
              onFilesChange={(files) => replaceFile('candidate', files)}
              files={candidate ? [candidate] : []}
              accept={PDF_ACCEPT}
              multiple={false}
              disabled={running}
              hint="Document to compare"
            />
          </div>
          <div className="sidebar-section compare-controls">
            <h3 className="sidebar-title">Comparison settings</h3>
            <label>
              Render resolution
              <select
                aria-label="Render resolution"
                value={renderDpi}
                onChange={(event) => setRenderDpi(
                  Number(event.target.value),
                )}
                disabled={running}
              >
                <option value={72}>72 DPI · faster</option>
                <option value={120}>120 DPI · recommended</option>
                <option value={144}>144 DPI · detailed</option>
                <option value={200}>200 DPI · maximum</option>
              </select>
            </label>
            <label>
              Pixel tolerance
              <input
                aria-label="Pixel tolerance"
                type="number"
                min="0"
                max="255"
                value={pixelTolerance}
                onChange={(event) => setPixelTolerance(
                  Number(event.target.value),
                )}
                disabled={running}
              />
              <span>
                Pixel tolerance ignores small channel noise.
              </span>
            </label>
            <label>
              Layout tolerance (points)
              <input
                aria-label="Layout tolerance"
                type="number"
                min="0.1"
                max="20"
                step="0.1"
                value={layoutTolerancePoints}
                onChange={(event) => setLayoutTolerancePoints(
                  Number(event.target.value),
                )}
                disabled={running}
              />
            </label>
          </div>
          <div className="sidebar-actions">
            {job && (
              <JobProgress
                job={job}
                connectionError={connectionError}
                onCancel={handleCancel}
              />
            )}
            {job?.status === 'COMPLETED' && job.outputs[0] && (
              <Button
                onClick={() => downloadOutput(job.outputs[0])}
                variant="outline"
                icon={<Download size={20} />}
                fullWidth
              >
                Download comparison again
              </Button>
            )}
            <Button
              onClick={handleSubmit}
              loading={running}
              disabled={running || !baseline || !candidate}
              icon={<GitCompareArrows size={20} />}
              fullWidth
              size="lg"
            >
              {running ? 'Comparing PDFs...' : 'Compare PDFs'}
            </Button>
          </div>
        </aside>

        <main className="operation-preview">
          <ComparePreview job={job} />
        </main>
      </div>
    </div>
  );
};

const ComparePreview = ({ job }) => {
  const {
    output,
    loading,
    report,
    error,
  } = useJobJsonReport(job, validateCompareReport, 'Comparison');

  if (!output) {
    return (
      <div className="compare-empty">
        <GitCompareArrows size={56} />
        <div>
          <span>Three comparison layers</span>
          <h2>Text, layout, and rendered-page evidence</h2>
          <p>
            Changed-page diff PNGs are packaged in the ZIP with the complete
            machine-readable report.
          </p>
        </div>
      </div>
    );
  }
  if (loading) {
    return <div className="compare-empty">Reading comparison report...</div>;
  }
  if (error || !report) {
    return (
      <div className="compare-empty compare-empty--error" role="alert">
        {error || 'The comparison report could not be read.'}
      </div>
    );
  }
  const changedPages = report.pages.filter((page) => (
    page.text.changed || page.layout.changed || page.visual.changed
  ));
  return (
    <div className="compare-result" data-testid="comparison-result">
      <header>
        <span>Comparison result</span>
        <h2>
          {report.status === 'identical'
            ? 'Documents are identical'
            : 'Documents differ'}
        </h2>
        <p>
          {report.summary.comparedPages} pages compared · maximum rendered
          difference {formatPercent(
            report.summary.maxVisualDifferencePercent,
          )}
        </p>
      </header>
      <div className="compare-summary-grid">
        <SummaryMetric
          value={report.summary.textChangedPages}
          label="text page"
        />
        <SummaryMetric
          value={report.summary.layoutChangedPages}
          label="layout page"
        />
        <SummaryMetric
          value={report.summary.visualChangedPages}
          label="visual page"
        />
      </div>
      {changedPages.length > 0 ? (
        <div className="compare-page-list">
          {changedPages.map((page) => (
            <PageDifference page={page} key={page.page} />
          ))}
        </div>
      ) : (
        <div className="compare-identical">
          No differences exceeded the selected tolerances.
        </div>
      )}
      <button
        className="compare-report-download"
        type="button"
        onClick={() => startBrowserDownload(
          jobService.getDownloadUrl(output),
          output.filename,
        )}
      >
        <FileJson size={16} />
        Download JSON report
      </button>
    </div>
  );
};

const SummaryMetric = ({ value, label }) => (
  <div>
    <strong>{value}</strong>
    <span>{label}{value === 1 ? '' : 's'}</span>
  </div>
);

const PageDifference = ({ page }) => (
  <section className="compare-page-result">
    <header>
      <h3>Page {page.page}</h3>
      <div>
        {page.text.changed && <span>Text</span>}
        {page.layout.changed && <span>Layout</span>}
        {page.visual.changed && <span>Visual</span>}
      </div>
    </header>
    <p>
      {page.text.addedLines || 0} added · {page.text.removedLines || 0} removed
      {' · '}{page.layout.movedTextLines || 0} moved lines
      {' · '}{formatPercent(page.visual.differencePercent || 0)} pixels
    </p>
    {page.text.changes?.length > 0 && (
      <ul>
        {page.text.changes.slice(0, 10).map((change, index) => (
          <li className={`is-${change.type}`} key={`${change.type}-${index}`}>
            <span>{change.type === 'added' ? '+' : '−'}</span>
            {change.text}
          </li>
        ))}
      </ul>
    )}
    {page.visual.diffImage && (
      <small>{page.visual.diffImage}</small>
    )}
  </section>
);

const validateCompareReport = (report) => {
  if (!['identical', 'different'].includes(report.status)
      || !report.summary
      || !Number.isInteger(report.summary.comparedPages)
      || !Array.isArray(report.pages)
      || report.pages.length !== report.summary.comparedPages) {
    throw new Error('The comparison report is invalid.');
  }
};

const formatPercent = (value) => `${Number(value).toFixed(2)}%`;

export default ComparePage;
