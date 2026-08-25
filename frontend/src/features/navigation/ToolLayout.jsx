import { Outlet } from 'react-router-dom';
import ToolTopbar from './ToolTopbar';
import './ToolLayout.css';

const ToolLayout = () => (
  <div className="tool-layout">
    <ToolTopbar />
    <div className="tool-layout__content">
      <Outlet />
    </div>
  </div>
);

export default ToolLayout;
