import { expect, test } from '@playwright/test';

test('converts and independently validates PDF/A', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('pdf-to-pdfa');
    expect(body).toContain('"profile":"pdfa-3b"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'pdfa-job',
        operation: 'pdf-to-pdfa',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/pdfa-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'pdfa-job',
  operation: 'pdf-to-pdfa',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'pdf-output',
    filename: 'report-pdfa-3b.pdf',
    mediaType: 'application/pdf',
    sizeBytes: 100,
    downloadUrl: '/api/v1/jobs/pdfa-job/outputs/pdf-output',
  }, {
    id: 'report-output',
    filename: 'report-pdfa-3b-validation-report.json',
    mediaType: 'application/json',
    sizeBytes: 200,
    downloadUrl: '/api/v1/jobs/pdfa-job/outputs/report-output',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/pdfa-job/outputs/pdf-output',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('%PDF'),
      });
    },
  );
  await page.route(
    '**/api/v1/jobs/pdfa-job/outputs/report-output',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'compliant',
          profile: 'pdfa-3b',
          compliant: true,
          totalAssertions: 142,
          failedChecks: 0,
          failures: [],
        }),
      });
    },
  );

  await page.goto('/pdf-to-pdfa');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 mock'),
  });
  await page.getByLabel('PDF/A profile').selectOption('pdfa-3b');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert to PDF/A',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report-pdfa-3b.pdf');
  await expect(page.getByText('veraPDF compliant')).toBeVisible();
  await expect(page.getByText('142 assertions checked')).toBeVisible();
});
