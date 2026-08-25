import { FileType } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';

const WORD_ACCEPT = {
  'application/msword': ['.doc'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': [
    '.docx',
  ],
};

const WordToPdfPage = () => (
  <OfficeToPdfPage
    operation="word-to-pdf"
    title="Word to PDF"
    description="Convert DOCX and DOC files with isolated LibreOffice Writer."
    Icon={FileType}
    uploadTitle="Upload Word document"
    accept={WORD_ACCEPT}
    hint="DOCX or DOC file"
    actionLabel="Convert Word to PDF"
    runningLabel="Converting Word..."
    successMessage="Converted PDF download started!"
    previewEyebrow="LibreOffice Writer"
    previewTitle="Preserve document layout"
    previewDescription="Paragraphs, tables, embedded images, headers, footers, and pagination are converted using the fonts installed on this server."
    fidelityWarning="Fonts unavailable on the server may be substituted, which can change line breaks or pagination."
  />
);

export default WordToPdfPage;
