import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('renders selected PDF pages to a JPG ZIP', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('pdf-to-jpg');
    expect(body).toContain('"pages":"1,3-4"');
    expect(body).toContain('"dpi":200');
    expect(body).toContain('"quality":72');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'pdf-jpg-job',
        operation: 'pdf-to-jpg',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/pdf-jpg-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'pdf-jpg-job',
  operation: 'pdf-to-jpg',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_jpg.zip',
    mediaType: 'application/zip',
    sizeBytes: 128,
    downloadUrl: '/api/v1/jobs/pdf-jpg-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/pdf-jpg-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/zip',
        body: Buffer.from('zip'),
      });
    },
  );

  await page.goto('/pdf-to-jpg');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await page.getByLabel('Pages').fill('1,3-4');
  await page.getByLabel('Resolution').selectOption('200');
  await page.getByLabel('JPEG quality').fill('72');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Convert & Download ZIP' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_jpg.zip');
  await expect(page.getByText('JPG archive download started!')).toBeVisible();
});
