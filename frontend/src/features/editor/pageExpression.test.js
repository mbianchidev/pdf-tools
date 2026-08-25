import { describe, expect, test } from 'vitest';
import { PageExpressionError, parsePageExpression } from './pageExpression';

describe('parsePageExpression', () => {
  test('parses keywords, closed ranges, and open ranges in order', () => {
    expect(parsePageExpression('1,3-5,even,7-', 10)).toEqual([
      1, 3, 4, 5, 2, 6, 8, 10, 7, 9,
    ]);
  });

  test('can keep or reject duplicates', () => {
    expect(parsePageExpression('1-2,2-3', 3, { duplicatePolicy: 'keep' }))
      .toEqual([1, 2, 2, 3]);

    expect(() => (
      parsePageExpression('1-2,2-3', 3, { duplicatePolicy: 'reject' })
    )).toThrowError(expect.objectContaining({
      code: 'DUPLICATE_PAGE',
    }));
  });

  test('rejects invalid pages with a structured error', () => {
    expect(() => parsePageExpression('8', 4)).toThrow(PageExpressionError);
    try {
      parsePageExpression('8', 4);
    } catch (error) {
      expect(error.code).toBe('PAGE_OUT_OF_RANGE');
      expect(error.token).toBe('8');
    }
  });

  test('rejects page numbers outside the safe integer range', () => {
    expect(() => parsePageExpression('999999999999999999999999', 4))
      .toThrowError(expect.objectContaining({
        code: 'INVALID_PAGE_NUMBER',
      }));
  });

  test('bounds expanded selections', () => {
    const repeatedAll = Array.from({ length: 11 }, () => 'all').join(',');

    expect(() => (
      parsePageExpression(repeatedAll, 10_000, { duplicatePolicy: 'keep' })
    )).toThrowError(expect.objectContaining({
      code: 'PAGE_SELECTION_TOO_LARGE',
    }));
  });

  test('does not overflow at the maximum 32-bit page', () => {
    expect(parsePageExpression('2147483647-', 2147483647))
      .toEqual([2147483647]);
  });
});
