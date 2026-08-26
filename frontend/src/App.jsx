import React, { lazy, Suspense, useState } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  Check,
  Code2,
  Copy,
  LockKeyhole,
} from 'lucide-react';
import Brand from './components/Brand';
import ToolLayout from './features/navigation/ToolLayout';
import {
  toolGroups,
  tools,
} from './features/navigation/toolCatalog';
import WorkspaceFileProvider from './features/navigation/WorkspaceFileProvider';
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
const UnlockPage = lazy(() => import('./pages/UnlockPage'));
const PdfToJpgPage = lazy(() => import('./pages/PdfToJpgPage'));
const JpgToPdfPage = lazy(() => import('./pages/JpgToPdfPage'));
const EditPage = lazy(() => import('./pages/EditPage'));
const WatermarkPage = lazy(() => import('./pages/WatermarkPage'));
const AddTextPage = lazy(() => import('./pages/AddTextPage'));
const SignaturePage = lazy(() => import('./pages/SignaturePage'));
const RedactPage = lazy(() => import('./pages/RedactPage'));
const PdfToMarkdownPage = lazy(() => import('./pages/PdfToMarkdownPage'));
const PdfToWordPage = lazy(() => import('./pages/PdfToWordPage'));
const PdfToPowerPointPage = lazy(
  () => import('./pages/PdfToPowerPointPage'),
);
const PdfToExcelPage = lazy(() => import('./pages/PdfToExcelPage'));
const WordToPdfPage = lazy(() => import('./pages/WordToPdfPage'));
const PowerPointToPdfPage = lazy(
  () => import('./pages/PowerPointToPdfPage'),
);
const ExcelToPdfPage = lazy(() => import('./pages/ExcelToPdfPage'));
const HtmlToPdfPage = lazy(() => import('./pages/HtmlToPdfPage'));
const CompressPage = lazy(() => import('./pages/CompressPage'));
const RepairPage = lazy(() => import('./pages/RepairPage'));
const PdfAPage = lazy(() => import('./pages/PdfAPage'));
const ComparePage = lazy(() => import('./pages/ComparePage'));
const NewPdfPage = lazy(() => import('./pages/NewPdfPage'));
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
            <div className="hero__actions">
              <Link className="button hero__workspace-link" to="/new">
                Open a PDF
                <ArrowRight aria-hidden="true" />
              </Link>
              <a className="text-link" href="#self-host">
                Self-host PDF Tools
                <ArrowRight aria-hidden="true" />
              </a>
            </div>
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
                <h2>Choose a PDF workflow.</h2>
              </div>
              <p>
                Open a tool to upload files, configure the operation, and
                download the result from your own instance.
              </p>
            </div>

            <div className="tool-columns">
              {toolGroups.map((group) => (
                <section className="tool-group" key={group}>
                  <h3>{group}</h3>
                  <div className="tool-list">
                    {tools
                      .filter((operation) => operation.group === group)
                      .map(({ id, icon: Icon, path, title, description }) => (
                        <Link className="tool-summary" key={id} to={path}>
                          <Icon className="tool-summary__icon" aria-hidden="true" />
                          <span>
                            <strong>{title}</strong>
                            <small>{description}</small>
                          </span>
                          <ArrowRight
                            className="tool-summary__arrow"
                            aria-hidden="true"
                          />
                        </Link>
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
    <WorkspaceFileProvider>
      <Suspense fallback={<div className="route-loading">Loading tool...</div>}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route element={<ToolLayout />}>
            <Route path="/new" element={<NewPdfPage />} />
            <Route path="/merge" element={<MergePage />} />
            <Route path="/split" element={<SplitPage />} />
            <Route path="/extract" element={<ExtractPage />} />
            <Route path="/remove" element={<RemovePage />} />
            <Route path="/rotate" element={<RotatePage />} />
            <Route path="/organize" element={<OrganizePage />} />
            <Route path="/crop" element={<CropPage />} />
            <Route path="/page-numbers" element={<PageNumbersPage />} />
            <Route path="/protect" element={<ProtectPage />} />
            <Route path="/unlock" element={<UnlockPage />} />
            <Route path="/pdf-to-jpg" element={<PdfToJpgPage />} />
            <Route path="/jpg-to-pdf" element={<JpgToPdfPage />} />
            <Route path="/watermark" element={<WatermarkPage />} />
            <Route path="/edit" element={<EditPage />} />
            <Route path="/add-text" element={<AddTextPage />} />
            <Route path="/signature" element={<SignaturePage />} />
            <Route path="/redact" element={<RedactPage />} />
            <Route path="/word-to-pdf" element={<WordToPdfPage />} />
            <Route
              path="/powerpoint-to-pdf"
              element={<PowerPointToPdfPage />}
            />
            <Route path="/excel-to-pdf" element={<ExcelToPdfPage />} />
            <Route path="/html-to-pdf" element={<HtmlToPdfPage />} />
            <Route path="/pdf-to-markdown" element={<PdfToMarkdownPage />} />
            <Route path="/convert-markdown" element={<PdfToMarkdownPage />} />
            <Route path="/pdf-to-word" element={<PdfToWordPage />} />
            <Route
              path="/pdf-to-powerpoint"
              element={<PdfToPowerPointPage />}
            />
            <Route path="/pdf-to-excel" element={<PdfToExcelPage />} />
            <Route path="/compress" element={<CompressPage />} />
            <Route path="/repair" element={<RepairPage />} />
            <Route path="/pdf-to-pdfa" element={<PdfAPage />} />
            <Route path="/compare" element={<ComparePage />} />
            <Route path="/convert-docx" element={<PdfToWordPage />} />
          </Route>
        </Routes>
      </Suspense>
    </WorkspaceFileProvider>
  );
}

export default App;
