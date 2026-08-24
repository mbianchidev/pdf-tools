import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('applies a styled watermark to selected pages', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('watermark');
    expect(body).toContain('"mode":"text"');
    expect(body).toContain('"pages":"1,3"');
    expect(body).toContain('"opacity":0.35');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'watermark-job',
        operation: 'watermark',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/watermark-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'watermark-job',
  operation: 'watermark',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_watermarked.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/watermark-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/watermark-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/watermark');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await page.getByLabel('Watermark text').fill('CONFIDENTIAL');
  await page.getByLabel('Pages').fill('1,3');
  await page.getByLabel('Opacity').fill('35');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Apply & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_watermarked.pdf');
  await expect(page.getByText('Watermarked PDF download started!'))
    .toBeVisible();
});
