import { expect, test } from '@playwright/test';

test('orders JPG files and creates a configured PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body.indexOf('second.jpg')).toBeLessThan(body.indexOf('first.jpg'));
    expect(body).toContain('jpg-to-pdf');
    expect(body).toContain('"pageSize":"letter"');
    expect(body).toContain('"orientation":"landscape"');
    expect(body).toContain('"margin":36');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'jpg-pdf-job',
        operation: 'jpg-to-pdf',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/jpg-pdf-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'jpg-pdf-job',
  operation: 'jpg-to-pdf',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'images.pdf',
    mediaType: 'application/pdf',
    sizeBytes: 128,
    downloadUrl: '/api/v1/jobs/jpg-pdf-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/jpg-pdf-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('%PDF-test'),
      });
    },
  );

  await page.goto('/jpg-to-pdf');
  await page.locator('input[type="file"]').first().setInputFiles([
    {
      name: 'first.jpg',
      mimeType: 'image/jpeg',
      buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
    },
    {
      name: 'second.jpg',
      mimeType: 'image/jpeg',
      buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9]),
    },
  ]);
  await page.getByRole('button', { name: 'Move second.jpg earlier' }).click();
  await page.getByLabel('Page size').selectOption('letter');
  await page.getByLabel('Orientation').selectOption('landscape');
  await page.getByLabel('Margin').fill('36');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Create & Download PDF' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('images.pdf');
  await expect(page.getByText('PDF download started!')).toBeVisible();
});
