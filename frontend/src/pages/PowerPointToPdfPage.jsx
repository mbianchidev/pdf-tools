import { Presentation } from 'lucide-react';
import OfficeToPdfPage from '../features/office/OfficeToPdfPage';

const POWERPOINT_ACCEPT = {
  'application/vnd.ms-powerpoint': ['.ppt'],
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': [
    '.pptx',
  ],
};

const PowerPointToPdfPage = () => (
  <OfficeToPdfPage
    operation="powerpoint-to-pdf"
    title="PowerPoint to PDF"
    description="Convert PPTX and PPT files with isolated LibreOffice Impress."
    Icon={Presentation}
    uploadTitle="Upload presentation"
    accept={POWERPOINT_ACCEPT}
    hint="PPTX or PPT file"
    actionLabel="Convert PowerPoint to PDF"
    runningLabel="Converting PowerPoint..."
    successMessage="Converted presentation download started!"
    previewEyebrow="LibreOffice Impress"
    previewTitle="Keep slide order and visuals"
    previewDescription="Slides, text, shapes, charts, and embedded images are rendered to PDF pages in presentation order."
    fidelityWarning="Animations, transitions, video, and unavailable fonts do not carry into PDF and can change visual fidelity."
  />
);

export default PowerPointToPdfPage;
