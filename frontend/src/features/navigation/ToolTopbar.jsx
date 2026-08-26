import { useEffect, useRef, useState } from 'react';
import { ChevronDown, FilePlus2 } from 'lucide-react';
import { NavLink, useLocation } from 'react-router-dom';
import Brand from '../../components/Brand';
import { findToolByPath, toolGroups, tools } from './toolCatalog';
import './ToolLayout.css';

const getToolGroupId = (group) => (
  `tool-group-${group.toLowerCase().replace(/\s+/g, '-')}`
);

const ToolTopbar = () => {
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const selectorRef = useRef(null);
  const triggerRef = useRef(null);
  const menuRef = useRef(null);
  const currentTool = findToolByPath(location.pathname);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const focusFrame = requestAnimationFrame(() => {
      menuRef.current?.querySelector('a')?.focus();
    });
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
        requestAnimationFrame(() => triggerRef.current?.focus());
      }
    };
    const handlePointerDown = (event) => {
      if (!selectorRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('pointerdown', handlePointerDown);
    return () => {
      cancelAnimationFrame(focusFrame);
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('pointerdown', handlePointerDown);
    };
  }, [open]);

  return (
    <header className="tool-topbar">
      <Brand compact />
      <nav className="tool-topbar__nav" aria-label="PDF workspace">
        <NavLink
          className={({ isActive }) => (
            `tool-topbar__new ${isActive ? 'is-active' : ''}`
          )}
          to="/new"
          aria-label="Start with a new PDF"
        >
          <FilePlus2 aria-hidden="true" />
          <span>New PDF</span>
        </NavLink>

        <div className="tool-topbar__selector" ref={selectorRef}>
          <button
            ref={triggerRef}
            className="tool-topbar__trigger"
            type="button"
            aria-controls="tool-switcher"
            aria-describedby="current-tool-name"
            aria-expanded={open}
            aria-label="Choose a tool"
            onClick={() => setOpen((current) => !current)}
          >
            <span>
              <small>Tool</small>
              <strong id="current-tool-name">
                {currentTool?.title || 'Choose a tool'}
              </strong>
            </span>
            <ChevronDown
              className={open ? 'is-open' : ''}
              aria-hidden="true"
            />
          </button>

          {open && (
            <div
              ref={menuRef}
              className="tool-topbar__menu"
              id="tool-switcher"
            >
              <div className="tool-topbar__menu-heading">
                <strong>Switch workflow</strong>
                <span>Your PDF stays available between PDF tools.</span>
              </div>
              <div className="tool-topbar__groups">
                {toolGroups.map((group) => (
                  <div
                    className="tool-topbar__group"
                    role="group"
                    aria-labelledby={getToolGroupId(group)}
                    key={group}
                  >
                    <span
                      className="tool-topbar__group-label"
                      id={getToolGroupId(group)}
                    >
                      {group}
                    </span>
                    {tools
                      .filter((tool) => tool.group === group)
                      .map(({ icon: Icon, id, path, title }) => (
                        <NavLink
                          aria-current={
                            currentTool?.id === id ? 'page' : undefined
                          }
                          className={`tool-topbar__link ${
                            currentTool?.id === id ? 'is-active' : ''
                          }`}
                          key={id}
                          onClick={() => setOpen(false)}
                          to={path}
                        >
                          <Icon aria-hidden="true" />
                          <span>{title}</span>
                        </NavLink>
                      ))}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </nav>
    </header>
  );
};

export default ToolTopbar;
