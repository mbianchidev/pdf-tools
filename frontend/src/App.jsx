import React, { lazy, Suspense, useState } from 'react';
import { Route, Routes } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  Check,
  Code2,
  Copy,
  Combine,
  Scissors,
  FileOutput,
  Trash2,
  Droplet,
  Type,
  PenTool,
  EyeOff,
  FileText,
  FileType,
  LockKeyhole,
  RotateCw,
  Layers3,
  Crop as CropIcon,
  ListOrdered,
} from 'lucide-react';
import Brand from './components/Brand';
import './App.css';

const MergePage = lazy(() => import('./pages/MergePage'));
const SplitPage = lazy(() => import('./pages/SplitPage'));
const ExtractPage = lazy(() => import('./pages/ExtractPage'));
const RemovePage = lazy(() => import('./pages/RemovePage'));
const RotatePage = lazy(() => import('./pages/RotatePage'));
const OrganizePage = lazy(() => import('./pages/OrganizePage'));
const CropPage = lazy(() => import('./pages/CropPage'));
const PageNumbersPage = lazy(() => import('./pages/PageNumbersPage'));
const ProtectPage = lazy(() => import('./pages/ProtectPage'));
const WatermarkPage = lazy(() => import('./pages/WatermarkPage'));
const AddTextPage = lazy(() => import('./pages/AddTextPage'));
const SignaturePage = lazy(() => import('./pages/SignaturePage'));
const RedactPage = lazy(() => import('./pages/RedactPage'));
const ConvertMarkdownPage = lazy(() => import('./pages/ConvertMarkdownPage'));
const ConvertDocxPage = lazy(() => import('./pages/ConvertDocxPage'));

const operations = [
  {
    id: 'merge',
    icon: Combine,
    group: 'Organize',
    title: 'Merge PDFs',
    description: 'Combine multiple PDF files into a single document',
  },
  {
    id: 'split',
    icon: Scissors,
    group: 'Organize',
    title: 'Split PDF',
    description: 'Split a PDF into multiple documents',
  },
  {
    id: 'extract',
    icon: FileOutput,
    group: 'Organize',
    title: 'Extract Pages',
    description: 'Extract specific pages from a PDF',
  },
  {
    id: 'remove',
    icon: Trash2,
    group: 'Organize',
    title: 'Remove Pages',
    description: 'Remove specific pages from a PDF',
  },
  {
    id: 'rotate',
    icon: RotateCw,
    group: 'Organize',
    title: 'Rotate PDF',
    description: 'Rotate all pages or adjust pages individually',
  },
  {
    id: 'organize',
    icon: Layers3,
    group: 'Organize',
    title: 'Organize PDF',
    description: 'Reorder, rotate, duplicate, and delete pages',
  },
  {
    id: 'crop',
    icon: CropIcon,
    group: 'Organize',
    title: 'Crop PDF',
    description: 'Apply shared or independent visual crop boxes',
  },
  {
    id: 'page-numbers',
    icon: ListOrdered,
    group: 'Mark up',
    title: 'Add Page Numbers',
    description: 'Number ranges with templates, fonts, and positions',
  },
  {
    id: 'protect',
    icon: LockKeyhole,
    group: 'Secure',
    title: 'Protect PDF',
    description: 'Encrypt with passwords and explicit permissions',
  },
  {
    id: 'watermark',
    icon: Droplet,
    group: 'Mark up',
    title: 'Add Watermark',
    description: 'Add a text watermark to all pages',
  },
  {
    id: 'add-text',
    icon: Type,
    group: 'Mark up',
    title: 'Add/Edit Text',
    description: 'Add or edit custom text at a specific position',
  },
  {
    id: 'add-signature',
    icon: PenTool,
    group: 'Mark up',
    title: 'Add Signature',
    description: 'Add a signature image to your PDF',
  },
  {
    id: 'redact',
    icon: EyeOff,
    group: 'Mark up',
    title: 'Redact Content',
    description: 'Redact sensitive information from your PDF',
  },
  {
    id: 'convert-markdown',
    icon: FileText,
    group: 'Convert',
    title: 'Convert to Markdown',
    description: 'Convert PDF to Markdown format',
  },
  {
    id: 'convert-docx',
    icon: FileType,
    group: 'Convert',
    title: 'Convert to DOCX',
    description: 'Convert PDF to Microsoft Word format',
  },
];

const toolGroups = ['Organize', 'Mark up', 'Secure', 'Convert'];
const INSTALL_COMMAND = `git clone https://github.com/mbianchidev/pdf-tools.git
cd pdf-tools
docker compose up --build`;

function HomePage() {
  const [copyStatus, setCopyStatus] = useState('idle');

  const copyInstallCommand = async () => {
    try {
      await navigator.clipboard.writeText(INSTALL_COMMAND);
      setCopyStatus('copied');
    } catch (error) {
      console.error('Failed to copy self-hosting commands:', error);
      setCopyStatus('failed');
    }
  };

  return (
    <div className="landing">
      <header className="landing__header shell">
        <Brand />
        <nav className="landing__nav" aria-label="Project links">
          <a href="#self-host">Self-host</a>
          <a
            className="button button--quiet button--small"
            href="https://github.com/mbianchidev/pdf-tools"
            target="_blank"
            rel="noreferrer"
          >
            <Code2 aria-hidden="true" />
            GitHub
          </a>
        </nav>
      </header>

      <main>
        <section className="hero shell">
          <motion.div
            className="hero__copy"
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          >
            <h1>
              Your PDFs.
              <span> Your server.</span>
            </h1>
            <p className="hero__lede">
              Deploy one open-source workspace to combine, organize, edit,
              convert, and secure documents under your own infrastructure.
            </p>
            <a className="text-link" href="#self-host">
              Self-host PDF Tools
              <ArrowRight aria-hidden="true" />
            </a>
            <p className="hero__trust">
              <LockKeyhole aria-hidden="true" />
              Open source · self-hosted · server-controlled files
            </p>
          </motion.div>

          <motion.div
            className="hero__visual"
            aria-label="PDF document workbench preview"
            initial={{ opacity: 0, rotate: -1, scale: 0.96 }}
            animate={{ opacity: 1, rotate: 0, scale: 1 }}
            transition={{ duration: 0.65, delay: 0.08, ease: [0.16, 1, 0.3, 1] }}
          >
            <div className="document-board" aria-hidden="true">
              <div className="document-board__top">
                <span />
                <span />
                <span />
                <b>PDF_WORKSPACE</b>
              </div>
              <div className="document-board__canvas">
                <div className="paper paper--back">
                  <i />
                  <i />
                  <i />
                </div>
                <div className="paper paper--middle">
                  <i />
                  <i />
                </div>
                <div className="paper paper--front">
                  <strong>PDF</strong>
                  <i />
                  <i />
                  <i />
                </div>
              </div>
              <div className="document-board__strip">
                <span>STREAMED</span>
                <span>V1 JOBS · 2H</span>
                <strong>READY</strong>
              </div>
            </div>
            <div className="hero__badge">Runs where you deploy it</div>
          </motion.div>
        </section>

        <section className="self-host" id="self-host">
          <div className="shell">
            <div className="section-heading">
              <div>
                <h2>Three steps to your own PDF workspace.</h2>
              </div>
              <p>
                Docker Compose provisions the app, PostgreSQL job metadata,
                and local streaming storage with one command.
              </p>
            </div>

            <ol className="deploy-steps">
              <li>
                <span>1</span>
                <div>
                  <strong>Clone the source</strong>
                  <p>Keep the deployment inspectable and under your control.</p>
                </div>
              </li>
              <li>
                <span>2</span>
                <div>
                  <strong>Start the stack</strong>
                  <p>Build the frontend, API, and PostgreSQL services.</p>
                </div>
              </li>
              <li>
                <span>3</span>
                <div>
                  <strong>Open your instance</strong>
                  <p>Use the workspace at your own hostname or localhost.</p>
                </div>
              </li>
            </ol>

            <div className="deploy-command" aria-label="Self-hosting commands">
              <div className="deploy-command__bar">
                <span />
                <span />
                <span />
                <b>TERMINAL</b>
                <button
                  type="button"
                  onClick={copyInstallCommand}
                  aria-label="Copy self-hosting commands"
                >
                  {copyStatus === 'copied'
                    ? <Check aria-hidden="true" />
                    : <Copy aria-hidden="true" />}
                  {copyStatus === 'copied'
                    ? 'Copied'
                    : copyStatus === 'failed' ? 'Select manually' : 'Copy'}
                </button>
              </div>
              <pre><code>{INSTALL_COMMAND}</code></pre>
              <div className="deploy-command__footer">
                <span>Open http://localhost</span>
                <strong>Docker 24+ · Compose v2</strong>
              </div>
            </div>

            <p className="deployment-note">
              For production, place the frontend behind TLS, inject database
              credentials from your secret store, and switch artifact storage
              to a private SeaweedFS S3 endpoint.
            </p>
          </div>
        </section>

        <section className="hosted-tools" id="tools">
          <div className="shell">
            <div className="section-heading">
              <div>
                <h2>What your instance provides.</h2>
              </div>
              <p>
                The installed workspace collects focused document workflows.
                Merge already uses persisted progress, cancellation,
                structured errors, and expiring outputs.
              </p>
            </div>

            <div className="tool-columns">
              {toolGroups.map((group) => (
                <section className="tool-group" key={group}>
                  <h3>{group}</h3>
                  <div className="tool-list">
                    {operations
                      .filter((operation) => operation.group === group)
                      .map(({ id, icon: Icon, title, description }) => (
                        <div className="tool-summary" key={id}>
                          <Icon aria-hidden="true" />
                          <span>
                            <strong>{title}</strong>
                            <small>{description}</small>
                          </span>
                        </div>
                      ))}
                  </div>
                </section>
              ))}
            </div>
          </div>
        </section>
      </main>

      <footer className="landing__footer shell">
        <Brand compact />
        <p>Open source, inspectable, and self-hosted under the MIT License.</p>
        <a
          href="https://github.com/mbianchidev/pdf-tools"
          target="_blank"
          rel="noreferrer"
        >
          Source code
          <ArrowRight aria-hidden="true" />
        </a>
      </footer>
    </div>
  );
}

function App() {
  return (
    <Suspense fallback={<div className="route-loading">Loading tool...</div>}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/merge" element={<MergePage />} />
        <Route path="/split" element={<SplitPage />} />
        <Route path="/extract" element={<ExtractPage />} />
        <Route path="/remove" element={<RemovePage />} />
        <Route path="/rotate" element={<RotatePage />} />
        <Route path="/organize" element={<OrganizePage />} />
        <Route path="/crop" element={<CropPage />} />
        <Route path="/page-numbers" element={<PageNumbersPage />} />
        <Route path="/protect" element={<ProtectPage />} />
        <Route path="/watermark" element={<WatermarkPage />} />
        <Route path="/add-text" element={<AddTextPage />} />
        <Route path="/signature" element={<SignaturePage />} />
        <Route path="/redact" element={<RedactPage />} />
        <Route path="/convert-markdown" element={<ConvertMarkdownPage />} />
        <Route path="/convert-docx" element={<ConvertDocxPage />} />
      </Routes>
    </Suspense>
  );
}

export default App;
