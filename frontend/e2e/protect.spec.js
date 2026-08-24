import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('protects a PDF with explicit permissions', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('protect');
    expect(body).toContain('"userPassword":"open-secret"');
    expect(body).toContain('"ownerPassword":"owner-secret"');
    expect(body).toContain('"print":"low"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'protect-job',
        operation: 'protect',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/protect-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'protect-job',
  operation: 'protect',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_protected.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/protect-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/protect-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: FOUR_PAGE_PDF,
    });
  });

  await page.goto('/protect');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await page.getByLabel('Open password', { exact: true }).fill('open-secret');
  await page.getByLabel('Confirm open password', { exact: true })
    .fill('open-secret');
  await page.getByLabel('Owner password', { exact: true }).fill('owner-secret');
  await page.getByLabel('Confirm owner password', { exact: true })
    .fill('owner-secret');
  await page.getByLabel('Printing').selectOption('low');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Protect & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_protected.pdf');
  await expect(page.getByText('Protected PDF download started!')).toBeVisible();
});
