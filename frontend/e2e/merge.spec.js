import { expect, test } from '@playwright/test';

test('merges files in the selected order and downloads the job output', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body.indexOf('second.pdf')).toBeLessThan(body.indexOf('first.pdf'));
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'merge-job',
        operation: 'merge',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/merge-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'merge-job',
  operation: 'merge',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'second_merged.pdf',
    mediaType: 'application/pdf',
    sizeBytes: 9,
    downloadUrl: '/api/v1/jobs/merge-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/merge-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: Buffer.from('%PDF-test'),
    });
  });

  await page.goto('/merge');
  await page.locator('input[type="file"]').first().setInputFiles([
    {
      name: 'first.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-first'),
    },
    {
      name: 'second.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-second'),
    },
  ]);
  await page.getByRole('button', { name: 'Move second.pdf up' }).click();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Merge & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('second_merged.pdf');
  await expect(page.getByText('PDFs merged successfully!')).toBeVisible();
});
