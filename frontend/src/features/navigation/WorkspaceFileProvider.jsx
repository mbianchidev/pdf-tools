import {
  useCallback,
  useMemo,
  useRef,
  useState,
} from 'react';
import { WorkspaceFileContext } from './WorkspaceFileContext';

const WorkspaceFileProvider = ({ children }) => {
  const [pdfFile, setPdfFile] = useState(null);
  const claimedLocationRef = useRef(null);

  const rememberPdfFile = useCallback((file, locationKey) => {
    setPdfFile(file);
    claimedLocationRef.current = file ? locationKey : null;
  }, []);

  const claimPdfFile = useCallback((locationKey) => {
    if (!pdfFile || claimedLocationRef.current === locationKey) {
      return null;
    }
    claimedLocationRef.current = locationKey;
    return pdfFile;
  }, [pdfFile]);

  const value = useMemo(() => ({
    claimPdfFile,
    pdfFile,
    rememberPdfFile,
  }), [claimPdfFile, pdfFile, rememberPdfFile]);

  return (
    <WorkspaceFileContext.Provider value={value}>
      {children}
    </WorkspaceFileContext.Provider>
  );
};

export default WorkspaceFileProvider;
