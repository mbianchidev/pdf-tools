const SHAPE_TYPES = new Set(['rectangle', 'ellipse']);
const COLOR_TYPES = new Set([
  'text',
  'rectangle',
  'ellipse',
  'line',
  'highlight',
  'note',
]);
const WIDTH_TYPES = new Set([
  'image',
  'rectangle',
  'ellipse',
  'highlight',
]);
const HEIGHT_TYPES = new Set(['rectangle', 'ellipse', 'highlight']);

export const MAX_EDIT_IMAGES = 10;

export const createEditElement = (id, type, page, x, y, imageId) => {
  const common = { id, type, page, x, y };
  switch (type) {
    case 'text':
      return {
        ...common,
        text: 'Text',
        font: 'helvetica',
        fontSize: 24,
        color: '#111111',
        opacity: 1,
        rotation: 0,
      };
    case 'image':
      return {
        ...common,
        width: 0.3,
        imageId,
        opacity: 1,
        rotation: 0,
      };
    case 'rectangle':
    case 'ellipse':
      return {
        ...common,
        width: 0.25,
        height: 0.15,
        strokeColor: '#4f46e5',
        fillColor: 'none',
        strokeWidth: 2,
        opacity: 1,
      };
    case 'line':
      return {
        ...common,
        x2: clamp(x + 0.25),
        y2: y,
        color: '#4f46e5',
        strokeWidth: 2,
        opacity: 1,
      };
    case 'highlight':
      return {
        ...common,
        width: 0.4,
        height: 0.08,
        color: '#f2ce57',
        opacity: 0.35,
      };
    case 'note':
      return {
        ...common,
        contents: 'Review this',
        title: 'PDF Tools',
        color: '#f2ce57',
      };
    default:
      return common;
  }
};

export const toEditOperationElement = (
  { id, imageId, ...element },
  imageIndices,
) => (
  element.type === 'image'
    ? { ...element, imageIndex: imageIndices.get(imageId) }
    : element
);

export const supportsEditColor = (type) => COLOR_TYPES.has(type);

export const editElementHasWidth = (type) => WIDTH_TYPES.has(type);

export const editElementHasHeight = (type) => HEIGHT_TYPES.has(type);

export const primaryEditColor = (element) => (
  SHAPE_TYPES.has(element.type) ? element.strokeColor : element.color
);

export const editColorChange = (type, color) => (
  SHAPE_TYPES.has(type) ? { strokeColor: color } : { color }
);

export const clamp = (value) => Math.min(Math.max(value, 0), 1);

export const validateEditElements = (elements, images) => {
  if (elements.length > 500) return 'Use at most 500 edit elements.';
  const imageIds = new Set(images.map((image) => image.id));
  for (const element of elements) {
    if (
      !Number.isInteger(element.page)
      || element.page < 1
      || !bounded(element.x)
      || !bounded(element.y)
    ) {
      return 'Every element must stay on a valid page position.';
    }
    if (
      Object.hasOwn(element, 'opacity')
      && (!Number.isFinite(element.opacity)
        || element.opacity < 0.05
        || element.opacity > 1)
    ) {
      return 'Element opacity must be between 5 and 100 percent.';
    }
    if (
      Object.hasOwn(element, 'rotation')
      && (!Number.isFinite(element.rotation)
        || element.rotation < -180
        || element.rotation > 180)
    ) {
      return 'Element rotation must be between -180 and 180 degrees.';
    }
    if (element.type === 'text') {
      if (
        !element.text.trim()
        || element.text.length > 200
        || !/^[\x20-\x7e]+$/.test(element.text)
      ) {
        return 'Text must use printable ASCII within 200 characters.';
      }
      if (element.fontSize < 8 || element.fontSize > 144) {
        return 'Text size must be between 8 and 144 points.';
      }
    }
    if (
      element.type === 'note'
      && (!element.contents.trim() || element.contents.length > 1000)
    ) {
      return 'Notes require contents within 1000 characters.';
    }
    if (element.type === 'image' && !imageIds.has(element.imageId)) {
      return 'Every image element must reference an uploaded image.';
    }
    if (
      editElementHasWidth(element.type)
      && (!Number.isFinite(element.width)
        || element.width < (element.type === 'image' ? 0.02 : 0.01)
        || element.width > 1)
    ) {
      return 'Element width must be between 1 and 100 percent.';
    }
    if (
      editElementHasHeight(element.type)
      && (!Number.isFinite(element.height)
        || element.height < 0.01
        || element.height > 1)
    ) {
      return 'Element height must be between 1 and 100 percent.';
    }
    if (
      element.type === 'line'
      && (!bounded(element.x2) || !bounded(element.y2))
    ) {
      return 'Line endpoints must stay within the page.';
    }
    if (
      element.type === 'highlight'
      && (element.x + element.width > 1
        || element.y + element.height > 1)
    ) {
      return 'Highlights must stay fully within the page.';
    }
  }
  return null;
};

const bounded = (value) => Number.isFinite(value)
  && value >= 0
  && value <= 1;
