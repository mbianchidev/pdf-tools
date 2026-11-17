import { useId, useMemo, useState } from 'react';
import { BookText } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';
import PrintSettingsControls from '../features/office/PrintSettingsControls';
import { usePrintSettings } from '../features/office/usePrintSettings';
import './MarkdownToPdfPage.css';

const MARKDOWN_ACCEPT = {
  'text/markdown': ['.md', '.markdown'],
  'text/x-markdown': ['.md', '.markdown'],
  'application/zip': ['.zip'],
  'application/x-zip-compressed': ['.zip'],
};
const MAX_PASTED_BYTES = 2 * 1024 * 1024;
const SOURCES = [
  ['upload', 'Upload file'],
  ['paste', 'Paste Markdown'],
];
const encoder = new TextEncoder();

const formatKib = (bytes) => `${(bytes / 1024).toFixed(1)} KiB`;

const MarkdownToPdfPage = () => {
  const { options, validationError: marginError, settings } =
    usePrintSettings();
  const [source, setSource] = useState('upload');
  const [markdown, setMarkdown] = useState('');
  const textareaId = useId();
  const statusId = useId();
  const pastedBytes = useMemo(
    () => encoder.encode(markdown).length,
    [markdown],
  );
  const pastedFile = useMemo(
    () => (markdown.trim()
      ? new File([markdown], 'document.md', { type: 'text/markdown' })
      : null),
    [markdown],
  );
  const pasteError = source === 'paste' && pastedBytes > MAX_PASTED_BYTES
    ? 'Pasted Markdown must be 2 MiB or smaller.'
    : null;

  return (
    <OfficeToPdfPage
      operation="markdown-to-pdf"
      title="Markdown to PDF"
      description="Render Markdown or a Markdown ZIP bundle with isolated Chromium."
      Icon={BookText}
      uploadTitle="Markdown source"
      accept={MARKDOWN_ACCEPT}
      hint="MD, MARKDOWN, or ZIP bundle"
      actionLabel="Convert Markdown to PDF"
      runningLabel="Rendering Markdown..."
      successMessage="Rendered Markdown download started!"
      previewEyebrow="flexmark + Chromium"
      previewTitle="Print GitHub-flavored Markdown"
      previewDescription="Headings, tables, alerts, task lists, code blocks, footnotes, emoji, links, and bundled images render with a clean print stylesheet."
      fidelityWarning="Raw HTML is shown as text. Remote images are not fetched; ZIP bundles such as PDF to Markdown output embed their PNG, JPEG, GIF, or WebP images."
      securityTitle="Isolated rendering"
      securityDescription="Markdown is parsed in a memory- and time-capped worker, then printed by Chromium in a networkless, read-only sidecar."
      options={options}
      validationError={marginError || pasteError}
      sourceFile={source === 'paste' ? pastedFile : undefined}
      renderSource={({ running, upload, clearResult }) => (
        <>
          <div
            className="split-mode-toggle"
            role="group"
            aria-label="Markdown source"
          >
            {SOURCES.map(([value, label]) => (
              <button
                type="button"
                key={value}
                className={`mode-btn ${source === value ? 'active' : ''}`}
                aria-pressed={source === value}
                onClick={() => {
                  setSource(value);
                  clearResult();
                }}
                disabled={running}
              >
                {label}
              </button>
            ))}
          </div>
          {source === 'upload' ? upload : (
            <div className="markdown-paste-field">
              <label htmlFor={textareaId}>Markdown text</label>
              <textarea
                id={textareaId}
                value={markdown}
                onChange={(event) => {
                  setMarkdown(event.target.value);
                  clearResult();
                }}
                aria-describedby={statusId}
                aria-invalid={Boolean(pasteError)}
                placeholder={'# Title\n\nWrite **Markdown** here.'}
                rows={14}
                spellCheck={false}
                disabled={running}
              />
              <span
                id={statusId}
                className={pasteError ? 'is-error' : ''}
                role={pasteError ? 'alert' : undefined}
              >
                {pasteError
                  || `${formatKib(pastedBytes)} of 2 MiB, saved as document.md`}
              </span>
            </div>
          )}
        </>
      )}
      renderControls={({ running }) => (
        <PrintSettingsControls
          settings={settings}
          running={running}
          error={marginError}
        />
      )}
    />
  );
};

export default MarkdownToPdfPage;
