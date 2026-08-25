import { useMemo, useState } from 'react';
import { Minimize2 } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import '../features/office/IsolatedConversionControls.css';
import './CompressPage.css';

const PDF_ACCEPT = {
  'application/pdf': ['.pdf'],
};

const MODES = {
  low: {
    label: 'Low',
    short: 'Lossless structural rewrite',
    preview: 'Keep original image data and rewrite PDF objects compactly.',
  },
  recommended: {
    label: 'Recommended',
    short: 'Balanced image compression',
    preview: 'Recompress oversized opaque images while retaining text and vectors.',
  },
  extreme: {
    label: 'Extreme',
    short: 'Smaller images, stronger compression',
    preview: 'Downsample opaque images further for the smallest practical result.',
  },
};

const CompressPage = () => {
  const [mode, setMode] = useState('recommended');
  const options = useMemo(() => ({ mode }), [mode]);
  const selected = MODES[mode];

  return (
    <OfficeToPdfPage
      operation="compress"
      title="Compress PDF"
      description="Reduce PDF size with an explicit quality and fidelity tradeoff."
      Icon={Minimize2}
      uploadTitle="Upload PDF"
      accept={PDF_ACCEPT}
      hint="One unencrypted PDF file"
      actionLabel="Compress PDF"
      runningLabel="Compressing PDF..."
      successMessage="Compressed PDF download started!"
      previewEyebrow={`${selected.label} compression`}
      previewTitle={selected.short}
      previewDescription={selected.preview}
      fidelityWarning="Text and vector content stay editable in every mode. Recommended and extreme use lossy JPEG recompression only for opaque raster images; masked and transparent images stay unchanged."
      securityTitle="Bounded isolated compression"
      securityDescription="PDFBox runs in a killable Java worker with page-tree, resource, image, pixel, output, heap, and wall-time limits. If compression cannot reduce the file, the exact original is returned under the compressed filename."
      options={options}
      renderControls={({ running }) => (
        <div className="sidebar-section conversion-controls">
          <h3 className="sidebar-title">Compression settings</h3>
          <label>
            Compression mode
            <select
              aria-label="Compression mode"
              value={mode}
              onChange={(event) => setMode(event.target.value)}
              disabled={running}
            >
              <option value="low">Low · lossless</option>
              <option value="recommended">Recommended · balanced</option>
              <option value="extreme">Extreme · smallest</option>
            </select>
          </label>
          <div className="compression-mode-guide">
            {Object.entries(MODES).map(([key, entry]) => (
              <div
                className={key === mode ? 'is-active' : ''}
                key={key}
              >
                <strong>{entry.label}</strong>
                <span>{entry.short}</span>
              </div>
            ))}
          </div>
        </div>
      )}
      renderResult={({ job, file }) => (
        <CompressionResult job={job} file={file} />
      )}
    />
  );
};

const CompressionResult = ({ job, file }) => {
  const output = job?.status === 'COMPLETED'
    ? job.outputs?.[0]
    : null;
  if (!file || !output || !Number.isFinite(output.sizeBytes)) {
    return null;
  }
  const savedBytes = Math.max(0, file.size - output.sizeBytes);
  const percentage = file.size > 0
    ? Math.round(savedBytes / file.size * 100)
    : 0;

  return (
    <div className="compression-result" aria-live="polite">
      <span className="compression-result__eyebrow">Size comparison</span>
      <strong>
        {savedBytes > 0 ? `${percentage}% smaller` : 'Already compact'}
      </strong>
      <dl>
        <div>
          <dt>Original</dt>
          <dd>{formatBytes(file.size)}</dd>
        </div>
        <div>
          <dt>Result</dt>
          <dd>{formatBytes(output.sizeBytes)}</dd>
        </div>
      </dl>
    </div>
  );
};

const formatBytes = (bytes) => {
  if (bytes < 1_024) {
    return `${new Intl.NumberFormat('en-US').format(bytes)} B`;
  }
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1_024;
  let unit = 0;
  while (value >= 1_024 && unit < units.length - 1) {
    value /= 1_024;
    unit++;
  }
  const digits = value >= 10 ? 0 : 1;
  return `${value.toFixed(digits)} ${units[unit]}`;
};

export default CompressPage;
