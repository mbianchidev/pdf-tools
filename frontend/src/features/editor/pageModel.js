const normalizeRotation = (rotation) => ((rotation % 360) + 360) % 360;

const defaultIdFactory = () => (
  globalThis.crypto?.randomUUID?.() || `page-${Date.now()}-${Math.random()}`
);

export const createPageModel = (
  pageCount,
  idFactory = defaultIdFactory,
  intrinsicRotations = [],
) => {
  if (!Number.isInteger(pageCount) || pageCount < 1) {
    throw new TypeError('Page count must be a positive integer.');
  }
  return Array.from({ length: pageCount }, (_, index) => {
    const intrinsicRotation = intrinsicRotations[index] ?? 0;
    if (!Number.isInteger(intrinsicRotation) || intrinsicRotation % 90 !== 0) {
      throw new TypeError('Intrinsic page rotation must be a multiple of 90 degrees.');
    }
    return {
      id: idFactory(),
      sourcePage: index + 1,
      intrinsicRotation: normalizeRotation(intrinsicRotation),
      rotation: 0,
    };
  });
};

export const pageRenderRotation = (page) => (
  normalizeRotation((page.intrinsicRotation ?? 0) + page.rotation)
);

export const movePage = (pages, fromIndex, toIndex) => {
  assertIndex(pages, fromIndex);
  assertIndex(pages, toIndex);
  const next = [...pages];
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  return next;
};

export const rotatePage = (pages, index, degrees = 90) => {
  assertIndex(pages, index);
  if (!Number.isInteger(degrees) || degrees % 90 !== 0) {
    throw new TypeError('Page rotation must be a multiple of 90 degrees.');
  }
  return pages.map((page, pageIndex) => (
    pageIndex === index
      ? { ...page, rotation: normalizeRotation(page.rotation + degrees) }
      : page
  ));
};

export const duplicatePage = (pages, index, idFactory = defaultIdFactory) => {
  assertIndex(pages, index);
  const next = [...pages];
  next.splice(index + 1, 0, { ...pages[index], id: idFactory() });
  return next;
};

export const removePage = (pages, index) => {
  assertIndex(pages, index);
  if (pages.length === 1) {
    throw new RangeError('A document must keep at least one page.');
  }
  return pages.filter((_, pageIndex) => pageIndex !== index);
};

const assertIndex = (pages, index) => {
  if (!Array.isArray(pages) || index < 0 || index >= pages.length) {
    throw new RangeError('Page index is outside the document.');
  }
};
