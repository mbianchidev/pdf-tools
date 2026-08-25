export const toNormalizedRectangle = (selection, bounds) => {
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) {
    throw new TypeError('Preview bounds must be positive.');
  }

  const x = (selection.left - bounds.left) / bounds.width;
  const y = (selection.top - bounds.top) / bounds.height;
  const width = selection.width / bounds.width;
  const height = selection.height / bounds.height;
  const values = [x, y, width, height];

  if (
    values.some((value) => !Number.isFinite(value))
    || x < 0
    || y < 0
    || width <= 0
    || height <= 0
    || x + width > 1
    || y + height > 1
  ) {
    throw new RangeError('Selection must be contained within the PDF preview.');
  }

  return { x, y, width, height };
};

export const normalizedRectangleStyle = ({ x, y, width, height }) => ({
  left: `${x * 100}%`,
  top: `${y * 100}%`,
  width: `${width * 100}%`,
  height: `${height * 100}%`,
});
