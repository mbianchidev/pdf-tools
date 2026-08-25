import { useState } from 'react';
import { CheckCircle2, FilePlus2 } from 'lucide-react';
import FileUpload from '../components/FileUpload';
import './NewPdfPage.css';

const NewPdfPage = () => {
  const [files, setFiles] = useState([]);
  const file = files[0] || null;

  return (
    <main className="new-pdf-page">
      <div className="new-pdf-page__inner">
        <header className="new-pdf-page__intro">
          <h1>Start with a PDF</h1>
          <p>
            Load a document, then open the tool switcher above to choose what
            you want to do. Your PDF follows you into compatible PDF tools.
          </p>
        </header>

        <section className="new-pdf-page__upload" aria-labelledby="new-pdf-upload">
          <div className="new-pdf-page__upload-heading">
            <span className="new-pdf-page__icon" aria-hidden="true">
              <FilePlus2 />
            </span>
            <div>
              <h2 id="new-pdf-upload">Load your PDF</h2>
              <p>Files stay in this browser session until you submit a tool.</p>
            </div>
          </div>
          <FileUpload
            files={files}
            hint="One PDF file"
            onFilesChange={setFiles}
          />

          {file && (
            <div className="new-pdf-page__ready" role="status">
              <CheckCircle2 aria-hidden="true" />
              <div>
                <strong>PDF ready</strong>
                <span>Open the Tool menu and choose your workflow.</span>
              </div>
            </div>
          )}
        </section>
      </div>
    </main>
  );
};

export default NewPdfPage;
