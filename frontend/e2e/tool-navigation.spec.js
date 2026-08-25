import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('starts with a PDF and carries it into a selected tool', async ({ page }) => {
  await page.goto('/new');

  await expect(page.getByRole('heading', { name: 'Start with a PDF' })).toBeVisible();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await expect(page.getByText('source.pdf')).toBeVisible();

  const toolSwitcher = page.getByRole('button', { name: 'Choose a tool' });
  await toolSwitcher.click();
  await expect(toolSwitcher).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('link', { name: /Merge PDFs/ })).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(toolSwitcher).toBeFocused();
  await expect(toolSwitcher).toHaveAttribute('aria-expanded', 'false');

  await toolSwitcher.click();
  await page.getByRole('link', { name: /Extract Pages/ }).click();

  await expect(page).toHaveURL('/extract');
  await expect(page.getByRole('heading', { name: 'Extract Pages' })).toBeVisible();
  await expect(page.getByText('source.pdf', { exact: true })).toBeVisible();
});

test('switches directly between tool pages', async ({ page }) => {
  await page.goto('/merge');

  const toolSwitcher = page.getByRole('button', { name: 'Choose a tool' });
  await expect(toolSwitcher).toContainText('Merge PDFs');
  await toolSwitcher.click();
  await page.getByRole('link', { name: /Compress PDF/ }).click();

  await expect(page).toHaveURL('/compress');
  await expect(page.getByRole('heading', { name: 'Compress PDF' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Choose a tool' }))
    .toContainText('Compress PDF');
});

test('keeps the tool switcher usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/merge');

  const toolSwitcher = page.getByRole('button', { name: 'Choose a tool' });
  await expect(toolSwitcher).toBeVisible();
  await toolSwitcher.click();
  await expect(page.getByText('Switch workflow')).toBeVisible();
  await page.getByRole('link', { name: /Add Signature/ }).click();

  await expect(page).toHaveURL('/signature');
  await expect(page.getByRole('heading', { name: 'Add Signature' })).toBeVisible();
});

test('hands off the first remaining merge file', async ({ page }) => {
  await page.goto('/merge');
  await page.locator('input[type="file"]').setInputFiles([
    {
      name: 'first.pdf',
      mimeType: 'application/pdf',
      buffer: FOUR_PAGE_PDF,
    },
    {
      name: 'second.pdf',
      mimeType: 'application/pdf',
      buffer: FOUR_PAGE_PDF,
    },
  ]);
  await page.getByRole('button', { name: 'Remove first.pdf' }).click();

  await page.getByRole('button', { name: 'Choose a tool' }).click();
  await page.getByRole('link', { name: /Extract Pages/ }).click();

  await expect(page.getByText('second.pdf', { exact: true })).toBeVisible();
  await expect(page.getByText('first.pdf', { exact: true })).toHaveCount(0);
});

test('keeps the remaining comparison PDF available', async ({ page }) => {
  await page.goto('/compare');
  const inputs = page.locator('input[type="file"]');
  await inputs.nth(0).setInputFiles({
    name: 'baseline.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await inputs.nth(1).setInputFiles({
    name: 'candidate.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await page.getByRole('button', { name: 'Remove file' }).nth(1).click();

  await page.getByRole('button', { name: 'Choose a tool' }).click();
  await page.getByRole('link', { name: /Extract Pages/ }).click();

  await expect(page.getByText('baseline.pdf', { exact: true })).toBeVisible();
});

test('keeps responsive workflows within the workspace viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/extract');

  const layout = page.locator('.tool-layout__content');
  const preview = page.locator('.operation-preview');
  const layoutBox = await layout.boundingBox();
  const previewBox = await preview.boundingBox();
  expect(previewBox.y + previewBox.height)
    .toBeLessThanOrEqual(layoutBox.y + layoutBox.height + 1);

  await page.goto('/compress');
  const operationPage = page.locator('.operation-page');
  await page.locator('.operation-content').evaluate((element) => {
    element.scrollTo(0, element.scrollHeight);
  });
  const operationBox = await operationPage.boundingBox();
  const conversionPreviewBox = await page.locator('.office-convert-preview')
    .boundingBox();
  expect(conversionPreviewBox.y + conversionPreviewBox.height)
    .toBeLessThanOrEqual(operationBox.y + operationBox.height + 1);
});
