import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, FilePlus2, Search } from 'lucide-react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import Brand from '../../components/Brand';
import { findToolByPath, toolGroups, tools } from './toolCatalog';
import './ToolLayout.css';

const getToolGroupId = (group) => (
  `tool-group-${group.toLowerCase().replace(/\s+/g, '-')}`
);

const matchesQuery = (tool, terms) => {
  const words = [tool.title, tool.description, tool.group]
    .join(' ')
    .toLowerCase()
    .split(/[^a-z0-9]+/);
  return terms.every((term) => words.some((word) => word.startsWith(term)));
};

const ToolTopbar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const selectorRef = useRef(null);
  const triggerRef = useRef(null);
  const menuRef = useRef(null);
  const currentTool = findToolByPath(location.pathname);
  const matchingTools = useMemo(() => {
    const terms = query.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
    return terms.length
      ? tools.filter((tool) => matchesQuery(tool, terms))
      : tools;
  }, [query]);
  const visibleGroups = toolGroups.filter(
    (group) => matchingTools.some((tool) => tool.group === group),
  );

  const closeMenu = () => {
    setOpen(false);
    setQuery('');
  };

  const handleSearchKeyDown = (event) => {
    if (event.key === 'Enter' && matchingTools.length > 0) {
      event.preventDefault();
      closeMenu();
      navigate(matchingTools[0].path);
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      menuRef.current?.querySelector('a')?.focus();
    }
  };

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const focusFrame = requestAnimationFrame(() => {
      menuRef.current?.querySelector('input')?.focus();
    });
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
        setQuery('');
        requestAnimationFrame(() => triggerRef.current?.focus());
      }
    };
    const handlePointerDown = (event) => {
      if (!selectorRef.current?.contains(event.target)) {
        setOpen(false);
        setQuery('');
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
            onClick={() => {
              setOpen((current) => !current);
              setQuery('');
            }}
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
              <label className="tool-topbar__search">
                <Search aria-hidden="true" />
                <input
                  type="search"
                  aria-label="Search tools"
                  aria-controls="tool-switcher-results"
                  placeholder="Search tools"
                  autoComplete="off"
                  spellCheck={false}
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  onKeyDown={handleSearchKeyDown}
                />
              </label>
              {matchingTools.length === 0 && (
                <p className="tool-topbar__empty" role="status">
                  No tools match “{query.trim()}”.
                </p>
              )}
              <div
                className="tool-topbar__groups"
                id="tool-switcher-results"
              >
                {visibleGroups.map((group) => (
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
                    {matchingTools
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
                          onClick={closeMenu}
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
