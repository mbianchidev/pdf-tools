import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('draws a secure redaction and downloads one sanitized PDF', async ({
  page,
}) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('redact');
    expect(body).toContain('"areas":[{"page":1');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'redact-job',
        operation: 'redact',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/redact-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'redact-job',
  operation: 'redact',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_redacted.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/redact-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/redact-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/redact');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  const canvas = page.getByRole('application', {
    name: 'Redaction canvas',
  });
  await expect(canvas).toBeVisible();
  const bounds = await canvas.boundingBox();
  await page.mouse.move(bounds.x + 60, bounds.y + 80);
  await page.mouse.down();
  await page.mouse.move(bounds.x + 280, bounds.y + 260);
  await page.mouse.up();
  await expect(page.getByText('1 area')).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Redact securely & Download',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_redacted.pdf');
  await expect(page.getByText(
    'Securely redacted PDF download started!',
  )).toBeVisible();
});
