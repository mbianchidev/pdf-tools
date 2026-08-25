import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('configures page numbers and downloads one PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('page-numbers');
    expect(body).toContain('"pages":"2-4"');
    expect(body).toContain('"start":5');
    expect(body).toContain('"position":"top-right"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'numbers-job',
        operation: 'page-numbers',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/numbers-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'numbers-job',
  operation: 'page-numbers',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_numbered.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/numbers-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/numbers-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: FOUR_PAGE_PDF,
    });
  });

  await page.goto('/page-numbers');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await expect(page.getByText('(page 1 of 4)', { exact: false })).toBeVisible();
  await page.getByLabel('Pages to number').fill('2-4');
  await page.getByLabel('Numbering starts at').fill('5');
  await page.getByLabel('Position').selectOption('top-right');
  await page.getByRole('button', { name: 'Next page' }).click();
  await expect(page.getByText('Page 5 of 4')).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Add Numbers & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_numbered.pdf');
  await expect(page.getByText('Numbered PDF download started!')).toBeVisible();
});
