import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('previews a crop and downloads one PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('crop');
    expect(body).toContain(
      '"crop":{"x":0.2,"y":0.1,"width":0.6,"height":0.8}',
    );
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'crop-job',
        operation: 'crop',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/crop-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'crop-job',
  operation: 'crop',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_cropped.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/crop-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/crop-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: FOUR_PAGE_PDF,
    });
  });

  await page.goto('/crop');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await expect(page.getByText('(page 1 of 4)', { exact: false })).toBeVisible();
  await page.getByLabel('Top margin (%)').fill('10');
  await page.getByLabel('Right margin (%)').fill('20');
  await page.getByLabel('Bottom margin (%)').fill('10');
  await page.getByLabel('Left margin (%)').fill('20');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Crop & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_cropped.pdf');
  await expect(page.getByText('Cropped PDF download started!')).toBeVisible();
});
