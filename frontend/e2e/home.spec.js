import { expect, test } from '@playwright/test';

test('lists the available PDF tools', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'PDF Tools' })).toBeVisible();
  await expect(page.getByText('Merge PDFs')).toBeVisible();
  await expect(page.getByText('Split PDF')).toBeVisible();
});
