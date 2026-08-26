import { useContext } from 'react';
import { WorkspaceFileContext } from './WorkspaceFileContext';

export const useWorkspaceFile = () => useContext(WorkspaceFileContext);
