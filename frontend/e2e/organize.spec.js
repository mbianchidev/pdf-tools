import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('organizes pages and downloads one PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('organize');
    expect(body).toContain(
      '"pages":[{"page":1,"rotation":0},{"page":1,"rotation":90},'
        + '{"page":4,"rotation":0},{"page":3,"rotation":0}]',
    );
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'organize-job',
        operation: 'organize',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/organize-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'organize-job',
  operation: 'organize',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_organized.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/organize-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route('**/api/v1/jobs/organize-job/outputs/output-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: FOUR_PAGE_PDF,
    });
  });

  await page.goto('/organize');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await expect(page.getByText('(4 output pages)', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Duplicate page 1' }).click();
  await page.getByRole('button', { name: 'Rotate page 2' }).click();
  await page.getByRole('button', { name: 'Move page 5 left' }).click();
  await page.getByRole('button', { name: 'Delete page 3' }).click();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Organize & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_organized.pdf');
  await expect(
    page.getByText('Organized PDF download started!'),
  ).toBeVisible();
});
