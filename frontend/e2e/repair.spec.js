import { expect, test } from '@playwright/test';

test('repairs a PDF and surfaces partial recovery warnings', async ({
  page,
}) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('repair');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'repair-job',
        operation: 'repair',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/repair-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'repair-job',
  operation: 'repair',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'pdf-output',
    filename: 'broken-repaired.pdf',
    mediaType: 'application/pdf',
    sizeBytes: 100,
    downloadUrl: '/api/v1/jobs/repair-job/outputs/pdf-output',
  }, {
    id: 'report-output',
    filename: 'broken-repair-report.json',
    mediaType: 'application/json',
    sizeBytes: 200,
    downloadUrl: '/api/v1/jobs/repair-job/outputs/report-output',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/repair-job/outputs/pdf-output',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('%PDF'),
      });
    },
  );
  await page.route(
    '**/api/v1/jobs/repair-job/outputs/report-output',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'partially-recovered',
          summary: 'qpdf recovered the PDF with warnings',
          recoveredPages: 2,
          warnings: ['Cross-reference table was reconstructed'],
        }),
      });
    },
  );

  await page.goto('/repair');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'broken.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 damaged mock'),
  });

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Repair PDF' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('broken-repaired.pdf');
  await expect(page.getByText(
    'Partially recovered',
    { exact: true },
  )).toBeVisible();
  await expect(page.getByText(
    'Cross-reference table was reconstructed',
  )).toBeVisible();
});
