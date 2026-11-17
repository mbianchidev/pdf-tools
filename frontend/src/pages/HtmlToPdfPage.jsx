import { CodeXml } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import PrintSettingsControls from '../features/office/PrintSettingsControls';
import { usePrintSettings } from '../features/office/usePrintSettings';

const HTML_ACCEPT = {
  'text/html': ['.html', '.htm'],
  'application/xhtml+xml': ['.html', '.htm'],
};

const HtmlToPdfPage = () => {
  const { options, validationError, settings } = usePrintSettings();

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
        <PrintSettingsControls
          settings={settings}
          running={running}
          error={validationError}
        />
      )}
    />
  );
};

export default HtmlToPdfPage;
