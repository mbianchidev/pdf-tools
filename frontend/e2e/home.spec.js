import { expect, test } from '@playwright/test';

test('explains how to self-host and launches tools from the landing page', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Your PDFs. Your server.' })).toBeVisible();
  await expect(page.getByText('docker compose up --build')).toBeVisible();
  await expect(page.getByText('Merge PDFs')).toBeVisible();
  await expect(page.getByText('Split PDF')).toBeVisible();
  const mergeLink = page.getByRole('link', { name: /Merge PDFs/ });
  await expect(mergeLink).toHaveAttribute('href', '/merge');
  await expect(page.getByRole('link', { name: /Add Signature/ }))
    .toHaveAttribute('href', '/signature');
  await expect(page.locator('link[rel="icon"][href="/favicon.svg"]')).toHaveCount(1);
  await page.getByRole('button', { name: 'Copy self-hosting commands' }).click();
  await expect(page.getByRole('button', { name: 'Copy self-hosting commands' }))
    .toContainText('Copied');
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
    .toContain('docker compose up --build');

  await page.keyboard.press('Tab');
  await expect(mergeLink).toBeFocused();
  await expect.poll(
    () => mergeLink.evaluate((link) => getComputedStyle(link).outlineStyle),
  ).toBe('solid');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL('/merge');
  await expect(page.getByRole('heading', { name: 'Merge PDFs' })).toBeVisible();
});
