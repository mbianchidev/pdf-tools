import { expect, test } from '@playwright/test';

test('configures PDF-to-Markdown and downloads ZIP', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('pdf-to-markdown');
    expect(body).toContain('"detectTables":true');
    expect(body).toContain('"includeImages":false');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'markdown-job',
        operation: 'pdf-to-markdown',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/markdown-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'markdown-job',
  operation: 'pdf-to-markdown',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'report-markdown.zip',
    mediaType: 'application/zip',
    sizeBytes: 4,
    downloadUrl: '/api/v1/jobs/markdown-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/markdown-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/zip',
        body: Buffer.from('PK\u0003\u0004'),
      });
    },
  );

  await page.goto('/pdf-to-markdown');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 mock'),
  });
  await page.getByLabel('Include extracted images').uncheck();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert PDF to Markdown',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report-markdown.zip');
  await expect(page.getByText(
    'Markdown bundle download started!',
  )).toBeVisible();
});
