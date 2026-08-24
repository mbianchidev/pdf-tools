import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('unlocks a PDF with its current password', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('unlock');
    expect(body).toContain('"password":"open-secret"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'unlock-job',
        operation: 'unlock',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/unlock-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'unlock-job',
  operation: 'unlock',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'locked_unlocked.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/unlock-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/unlock-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/unlock');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'locked.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  await page.getByLabel('Current password').fill('open-secret');
  await page.getByLabel('Confirm password').fill('open-secret');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Unlock & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('locked_unlocked.pdf');
  await expect(page.getByText('Unlocked PDF download started!')).toBeVisible();
});
