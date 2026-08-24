import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('rotates all pages with a per-page adjustment', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('rotate');
    expect(body).toContain('"pages":"1,3,4"');
    expect(body).toContain('"pages":"2","rotation":180');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'rotate-job',
        operation: 'rotate',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/rotate-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'rotate-job',
  operation: 'rotate',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_rotated.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/rotate-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/rotate-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: FOUR_PAGE_PDF,
    });
  });

  await page.goto('/rotate');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await expect(page.getByText('(4 pages)', { exact: false })).toBeVisible();
  await page.getByRole('button', {
    name: 'Rotate all pages 90 degrees',
  }).click();
  await page.getByRole('button', { name: 'Rotate page 2' }).click();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Rotate & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_rotated.pdf');
  await expect(page.getByText('Rotated PDF download started!')).toBeVisible();
});
