import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('configures HTML rendering and downloads one PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('html-to-pdf');
    expect(body).toContain('"pageSize":"letter"');
    expect(body).toContain('"orientation":"landscape"');
    expect(body).toContain('"marginMm":14');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'html-job',
        operation: 'html-to-pdf',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/html-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'html-job',
  operation: 'html-to-pdf',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'report.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/html-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/html-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/html-to-pdf');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.html',
    mimeType: 'text/html',
    buffer: Buffer.from('<!doctype html><h1>Report</h1>'),
  });
  await page.getByLabel('Paper size').selectOption('letter');
  await page.getByLabel('Page orientation').selectOption('landscape');
  await page.getByLabel('Page margin').fill('14');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert HTML to PDF',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report.pdf');
  await expect(page.getByText(
    'Rendered HTML download started!',
  )).toBeVisible();
});
