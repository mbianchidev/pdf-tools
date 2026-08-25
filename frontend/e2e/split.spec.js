import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('splits fixed-size groups and downloads one ZIP', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('split');
    expect(body).toContain('"mode":"fixed"');
    expect(body).toContain('"fixedGroupSize":2');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'split-job',
        operation: 'split',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/split-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'split-job',
  operation: 'split',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_split.zip',
    mediaType: 'application/zip',
    sizeBytes: 9,
    downloadUrl: '/api/v1/jobs/split-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/split-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/zip',
      body: Buffer.from('PK-split'),
    });
  });

  await page.goto('/split');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await expect(page.getByText('(4 pages)', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Fixed' }).click();
  await page.getByLabel('Pages per output').fill('2');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Split & Download ZIP' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_split.zip');
  await expect(page.getByText('PDF split download started!')).toBeVisible();
});
