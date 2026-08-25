import { describe, expect, test } from 'vitest';
import {
  createPageModel,
  duplicatePage,
  movePage,
  pageRenderRotation,
  removePage,
  rotatePage,
} from './pageModel';

describe('page model', () => {
  test('supports deterministic reorder, rotation, duplication, and deletion', () => {
    let nextId = 0;
    const idFactory = () => `id-${nextId += 1}`;
    let pages = createPageModel(3, idFactory);

    pages = movePage(pages, 2, 0);
    pages = rotatePage(pages, 0);
    pages = duplicatePage(pages, 0, idFactory);
    pages = removePage(pages, 2);

    expect(pages).toEqual([
      { id: 'id-3', sourcePage: 3, intrinsicRotation: 0, rotation: 90 },
      { id: 'id-4', sourcePage: 3, intrinsicRotation: 0, rotation: 90 },
      { id: 'id-2', sourcePage: 2, intrinsicRotation: 0, rotation: 0 },
    ]);
  });

  test('never permits deleting every page', () => {
    expect(() => removePage(createPageModel(1), 0))
      .toThrow('A document must keep at least one page.');
  });

  test('combines intrinsic and user-applied rotation', () => {
    const pages = createPageModel(1, () => 'page-1', [90]);
    const rotated = rotatePage(pages, 0, 90);

    expect(pageRenderRotation(rotated[0])).toBe(180);
  });
});
