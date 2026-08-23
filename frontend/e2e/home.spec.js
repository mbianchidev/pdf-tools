import { expect, test } from '@playwright/test';

test('explains how to self-host without launching tools from the landing page', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Your PDFs. Your server.' })).toBeVisible();
  await expect(page.getByText('docker compose up --build')).toBeVisible();
  await expect(page.getByText('Merge PDFs')).toBeVisible();
  await expect(page.getByText('Split PDF')).toBeVisible();
  await expect(page.locator('a[href="/merge"]')).toHaveCount(0);
  await expect(page.locator('link[rel="icon"][href="/favicon.svg"]')).toHaveCount(1);
  await page.getByRole('button', { name: 'Copy self-hosting commands' }).click();
  await expect(page.getByRole('button', { name: 'Copy self-hosting commands' }))
    .toContainText('Copied');
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toContain('docker compose up --build');
});
