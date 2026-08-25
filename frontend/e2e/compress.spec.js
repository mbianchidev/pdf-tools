import { expect, test } from '@playwright/test';

test('compresses a PDF and compares sizes', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('compress');
    expect(body).toContain('"mode":"extreme"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'compress-job',
        operation: 'compress',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/compress-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'compress-job',
  operation: 'compress',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'report-compressed.pdf',
    mediaType: 'application/pdf',
    sizeBytes: 600,
    downloadUrl: '/api/v1/jobs/compress-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/compress-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('%PDF'),
      });
    },
  );

  await page.goto('/compress');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.concat([
      Buffer.from('%PDF-1.7 mock'),
      Buffer.alloc(987),
    ]),
  });
  await page.getByLabel('Compression mode').selectOption('extreme');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Compress PDF' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report-compressed.pdf');
  await expect(page.getByText(/smaller/)).toBeVisible();
  await expect(page.getByText(
    'Compressed PDF download started!',
  )).toBeVisible();
});
