const PAGE = /^\d+$/;
const RANGE = /^(\d*)\s*-\s*(\d*)$/;
const MAX_EXPANDED_SELECTIONS = 100_000;

export class PageExpressionError extends Error {
  constructor(code, message, token = null) {
    super(message);
    this.name = 'PageExpressionError';
    this.code = code;
    this.token = token;
  }
}

export const parsePageExpression = (
  expression,
  pageCount,
  { duplicatePolicy = 'deduplicate' } = {},
) => {
  if (!Number.isInteger(pageCount) || pageCount < 1) {
    throw new PageExpressionError(
      'INVALID_PAGE_COUNT',
      'The document must contain at least one page.',
    );
  }
  if (!expression?.trim()) {
    throw new PageExpressionError(
      'EMPTY_PAGE_EXPRESSION',
      'Enter at least one page or range.',
    );
  }
  if (expression.length > 4096) {
    throw new PageExpressionError(
      'PAGE_EXPRESSION_TOO_LONG',
      'The page expression exceeds 4096 characters.',
    );
  }
  if (!['keep', 'deduplicate', 'reject'].includes(duplicatePolicy)) {
    throw new TypeError(`Unknown duplicate policy: ${duplicatePolicy}`);
  }

  const result = [];
  const seen = new Set();
  let expandedSelections = 0;
  for (const rawToken of expression.split(',')) {
    const token = rawToken.trim().toLowerCase();
    if (!token) {
      throw new PageExpressionError(
        'INVALID_PAGE_TOKEN',
        'Page expressions cannot contain empty items.',
        rawToken,
      );
    }

    const expanded = expandToken(token, pageCount);
    expandedSelections += expanded.length;
    if (expandedSelections > MAX_EXPANDED_SELECTIONS) {
      throw new PageExpressionError(
        'PAGE_SELECTION_TOO_LARGE',
        `The page expression expands beyond ${MAX_EXPANDED_SELECTIONS} selections.`,
        token,
      );
    }
    for (const page of expanded) {
      if (seen.has(page)) {
        if (duplicatePolicy === 'reject') {
          throw new PageExpressionError(
            'DUPLICATE_PAGE',
            `Page ${page} is selected more than once.`,
            token,
          );
        }
        if (duplicatePolicy === 'deduplicate') {
          continue;
        }
      }
      seen.add(page);
      result.push(page);
    }
  }
  if (result.length === 0) {
    throw new PageExpressionError(
      'EMPTY_PAGE_SELECTION',
      'The page expression does not select any pages.',
      expression,
    );
  }
  return result;
};

const expandToken = (token, pageCount) => {
  if (token === 'all') return createRange(1, pageCount);
  if (token === 'odd') return createRange(1, pageCount, 2);
  if (token === 'even') return createRange(2, pageCount, 2);

  if (PAGE.test(token)) {
    return [validatePage(Number(token), pageCount, token)];
  }

  const match = token.match(RANGE);
  if (!match || (!match[1] && !match[2])) {
    throw new PageExpressionError(
      'INVALID_PAGE_TOKEN',
      `Invalid page token: ${token}`,
      token,
    );
  }

  const start = match[1] ? Number(match[1]) : 1;
  const end = match[2] ? Number(match[2]) : pageCount;
  validatePage(start, pageCount, token);
  validatePage(end, pageCount, token);
  if (start > end) {
    throw new PageExpressionError(
      'DESCENDING_PAGE_RANGE',
      `Page range start must not exceed its end: ${token}`,
      token,
    );
  }
  return createRange(start, end);
};

const validatePage = (page, pageCount, token) => {
  if (!Number.isSafeInteger(page)) {
    throw new PageExpressionError(
      'INVALID_PAGE_NUMBER',
      `Page number is too large: ${token}`,
      token,
    );
  }
  if (page < 1 || page > pageCount) {
    throw new PageExpressionError(
      'PAGE_OUT_OF_RANGE',
      `Page ${page} is outside the valid range 1-${pageCount}.`,
      token,
    );
  }
  return page;
};

const createRange = (start, end, step = 1) => {
  const count = start > end ? 0 : Math.floor((end - start) / step) + 1;
  if (count > MAX_EXPANDED_SELECTIONS) {
    throw new PageExpressionError(
      'PAGE_SELECTION_TOO_LARGE',
      `A page range cannot expand beyond ${MAX_EXPANDED_SELECTIONS} selections.`,
      `${start}-${end}`,
    );
  }
  const pages = [];
  let page = start;
  for (let index = 0; index < count; index += 1) {
    pages.push(page);
    page += step;
  }
  return pages;
};
