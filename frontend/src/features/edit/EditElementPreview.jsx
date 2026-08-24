import { NotepadText } from 'lucide-react';
import { primaryEditColor } from './editElementModel';

const EDIT_FONT_FAMILIES = {
  helvetica: 'Arial, sans-serif',
  'helvetica-bold': 'Arial, sans-serif',
  times: 'Times New Roman, serif',
  'times-bold': 'Times New Roman, serif',
  courier: 'Courier New, monospace',
  'courier-bold': 'Courier New, monospace',
};

const EditElementPreview = ({
  element,
  images,
  selected,
  onSelect,
  selectMode,
  renderWidth,
  renderHeight,
  pageWidth,
  onImageDimensions,
}) => {
  const common = {
    left: `${element.x * 100}%`,
    top: `${element.y * 100}%`,
    pointerEvents: selectMode ? 'auto' : 'none',
  };
  const className = [
    'edit-element',
    `edit-element--${element.type}`,
    selected ? 'selected' : '',
  ].join(' ');
  const select = (event) => {
    if (!selectMode) return;
    event.stopPropagation();
    onSelect(element.id);
  };

  if (element.type === 'text') {
    return (
      <span
        className={className}
        style={{
          ...common,
          color: element.color,
          opacity: element.opacity,
          fontFamily: EDIT_FONT_FAMILIES[element.font],
          fontWeight: element.font.includes('bold') ? 700 : 400,
          fontSize: `${element.fontSize * renderWidth / pageWidth}px`,
          transform: `translate(-50%, -50%) rotate(${element.rotation}deg)`,
        }}
        onPointerDown={select}
      >
        {element.text}
      </span>
    );
  }

  if (element.type === 'image') {
    const image = images.find((item) => item.id === element.imageId);
    const fitted = image?.width && image?.height
      ? fitEditImage(
          image,
          element.width * renderWidth,
          renderHeight,
        )
      : null;
    return (
      <img
        className={className}
        src={image?.url}
        alt=""
        onLoad={(event) => onImageDimensions(
          element.imageId,
          event.currentTarget.naturalWidth,
          event.currentTarget.naturalHeight,
        )}
        style={{
          ...common,
          width: fitted
            ? `${fitted.width}px`
            : `${element.width * 100}%`,
          height: fitted ? `${fitted.height}px` : 'auto',
          opacity: element.opacity,
          transform: `translate(-50%, -50%) rotate(${element.rotation}deg)`,
        }}
        onPointerDown={select}
      />
    );
  }

  if (element.type === 'line') {
    const dx = (element.x2 - element.x) * renderWidth;
    const dy = (element.y2 - element.y) * renderHeight;
    const length = Math.hypot(dx, dy) / renderWidth * 100;
    const angle = Math.atan2(dy, dx) * 180 / Math.PI;
    return (
      <span
        className={className}
        style={{
          ...common,
          width: `${length}%`,
          borderTopColor: element.color,
          opacity: element.opacity,
          transform: `rotate(${angle}deg)`,
        }}
        onPointerDown={select}
      />
    );
  }

  if (element.type === 'note') {
    return (
      <span
        className={className}
        style={{ ...common, color: element.color }}
        onPointerDown={select}
      >
        <NotepadText size={22} />
      </span>
    );
  }

  return (
    <span
      className={className}
      style={{
        ...common,
        width: `${element.width * 100}%`,
        height: `${element.height * 100}%`,
        color: primaryEditColor(element),
        background: element.type === 'highlight'
          ? element.color
          : element.fillColor === 'none'
            ? 'transparent'
            : element.fillColor,
        opacity: element.opacity,
      }}
      onPointerDown={select}
    />
  );
};

const fitEditImage = (image, requestedWidth, pageHeight) => {
  const requestedHeight = requestedWidth * image.height / image.width;
  const scale = requestedHeight > pageHeight
    ? pageHeight / requestedHeight
    : 1;
  return {
    width: requestedWidth * scale,
    height: requestedHeight * scale,
  };
};

export default EditElementPreview;
