import './IsolatedConversionControls.css';

const PrintSettingsControls = ({ settings, running, error }) => (
  <div className="sidebar-section conversion-controls">
    <h3 className="sidebar-title">Print settings</h3>
    <label>
      Paper size
      <select
        aria-label="Paper size"
        value={settings.pageSize}
        onChange={(event) => settings.setPageSize(event.target.value)}
        disabled={running}
      >
        <option value="a4">A4</option>
        <option value="letter">Letter</option>
        <option value="legal">Legal</option>
      </select>
    </label>
    <label>
      Orientation
      <select
        aria-label="Page orientation"
        value={settings.orientation}
        onChange={(event) => settings.setOrientation(event.target.value)}
        disabled={running}
      >
        <option value="portrait">Portrait</option>
        <option value="landscape">Landscape</option>
      </select>
    </label>
    <label>
      Margin (mm)
      <input
        aria-label="Page margin"
        type="number"
        min="0"
        max="50"
        step="1"
        value={settings.marginMm}
        onChange={(event) => settings.setMarginMm(event.target.value)}
        disabled={running}
      />
    </label>
    <label className="conversion-toggle">
      <input
        type="checkbox"
        checked={settings.printBackground}
        onChange={(event) => settings.setPrintBackground(event.target.checked)}
        disabled={running}
      />
      Print CSS backgrounds
    </label>
    {error && (
      <p className="conversion-error" role="alert">
        {error}
      </p>
    )}
  </div>
);

export default PrintSettingsControls;
