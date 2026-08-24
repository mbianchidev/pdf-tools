import { Trash2 } from 'lucide-react';
import {
  editColorChange,
  editElementHasHeight,
  editElementHasWidth,
  primaryEditColor,
  supportsEditColor,
} from './editElementModel';

const EditPropertiesPanel = ({
  selected,
  images,
  running,
  onChange,
  onDelete,
}) => (
  <div className="sidebar-section edit-properties">
    <div className="edit-properties__heading">
      <h3 className="sidebar-title">Selected {selected.type}</h3>
      <button
        type="button"
        onClick={onDelete}
        disabled={running}
        aria-label="Delete selected element"
      >
        <Trash2 size={16} />
      </button>
    </div>
    {selected.type === 'text' && (
      <label>
        Text
        <input
          aria-label="Text"
          maxLength={200}
          value={selected.text}
          onChange={(event) => onChange({ text: event.target.value })}
          disabled={running}
        />
      </label>
    )}
    {selected.type === 'note' && (
      <label>
        Contents
        <textarea
          aria-label="Contents"
          maxLength={1000}
          value={selected.contents}
          onChange={(event) => onChange({
            contents: event.target.value,
          })}
          disabled={running}
        />
      </label>
    )}
    {selected.type === 'image' && (
      <label>
        Image
        <select
          aria-label="Image"
          value={selected.imageId}
          onChange={(event) => onChange({ imageId: event.target.value })}
          disabled={running}
        >
          {images.map((image) => (
            <option value={image.id} key={image.id}>
              {image.file.name}
            </option>
          ))}
        </select>
      </label>
    )}
    {supportsEditColor(selected.type) && (
      <label>
        Color
        <input
          aria-label="Element color"
          type="color"
          value={primaryEditColor(selected)}
          onChange={(event) => onChange(
            editColorChange(selected.type, event.target.value),
          )}
          disabled={running}
        />
      </label>
    )}
    {selected.type === 'text' && (
      <div className="edit-two-columns">
        <label>
          Font
          <select
            aria-label="Font"
            value={selected.font}
            onChange={(event) => onChange({ font: event.target.value })}
            disabled={running}
          >
            <option value="helvetica">Helvetica</option>
            <option value="helvetica-bold">Helvetica Bold</option>
            <option value="times">Times</option>
            <option value="times-bold">Times Bold</option>
            <option value="courier">Courier</option>
            <option value="courier-bold">Courier Bold</option>
          </select>
        </label>
        <label>
          Font size
          <input
            aria-label="Font size"
            type="number"
            min="8"
            max="144"
            value={selected.fontSize}
            onChange={(event) => onChange({
              fontSize: Number(event.target.value),
            })}
            disabled={running}
          />
        </label>
      </div>
    )}
    {editElementHasWidth(selected.type) && (
      <div className="edit-two-columns">
        <label>
          Width
          <input
            aria-label="Element width"
            type="number"
            min={selected.type === 'image' ? 2 : 1}
            max="100"
            value={Math.round(selected.width * 100)}
            onChange={(event) => onChange({
              width: Number(event.target.value) / 100,
            })}
            disabled={running}
          />
        </label>
        {editElementHasHeight(selected.type) && (
          <label>
            Height
            <input
              aria-label="Element height"
              type="number"
              min="1"
              max="100"
              value={Math.round(selected.height * 100)}
              onChange={(event) => onChange({
                height: Number(event.target.value) / 100,
              })}
              disabled={running}
            />
          </label>
        )}
      </div>
    )}
    {['rectangle', 'ellipse'].includes(selected.type) && (
      <>
        <label className="edit-check">
          <input
            type="checkbox"
            checked={selected.fillColor !== 'none'}
            onChange={(event) => onChange({
              fillColor: event.target.checked ? '#f2ce57' : 'none',
            })}
            disabled={running}
          />
          Fill shape
        </label>
        {selected.fillColor !== 'none' && (
          <label>
            Fill color
            <input
              aria-label="Fill color"
              type="color"
              value={selected.fillColor}
              onChange={(event) => onChange({
                fillColor: event.target.value,
              })}
              disabled={running}
            />
          </label>
        )}
      </>
    )}
    {selected.type === 'line' && (
      <div className="edit-two-columns">
        <label>
          End X
          <input
            aria-label="Line end X"
            type="number"
            min="0"
            max="100"
            value={Math.round(selected.x2 * 100)}
            onChange={(event) => onChange({
              x2: Number(event.target.value) / 100,
            })}
            disabled={running}
          />
        </label>
        <label>
          End Y
          <input
            aria-label="Line end Y"
            type="number"
            min="0"
            max="100"
            value={Math.round(selected.y2 * 100)}
            onChange={(event) => onChange({
              y2: Number(event.target.value) / 100,
            })}
            disabled={running}
          />
        </label>
      </div>
    )}
    {Object.hasOwn(selected, 'opacity') && (
      <label>
        Opacity
        <input
          aria-label="Element opacity"
          type="number"
          min="5"
          max="100"
          value={Math.round(selected.opacity * 100)}
          onChange={(event) => onChange({
            opacity: Number(event.target.value) / 100,
          })}
          disabled={running}
        />
      </label>
    )}
    {Object.hasOwn(selected, 'rotation') && (
      <label>
        Rotation
        <input
          aria-label="Element rotation"
          type="number"
          min="-180"
          max="180"
          value={selected.rotation}
          onChange={(event) => onChange({
            rotation: Number(event.target.value),
          })}
          disabled={running}
        />
      </label>
    )}
    <div className="edit-two-columns">
      <label>
        X
        <input
          aria-label="Element X"
          type="number"
          min="0"
          max="100"
          value={Math.round(selected.x * 100)}
          onChange={(event) => onChange({
            x: Number(event.target.value) / 100,
          })}
          disabled={running}
        />
      </label>
      <label>
        Y
        <input
          aria-label="Element Y"
          type="number"
          min="0"
          max="100"
          value={Math.round(selected.y * 100)}
          onChange={(event) => onChange({
            y: Number(event.target.value) / 100,
          })}
          disabled={running}
        />
      </label>
    </div>
  </div>
);

export default EditPropertiesPanel;
