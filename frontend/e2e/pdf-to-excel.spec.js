import { expect, test } from '@playwright/test';

test('configures PDF-to-Excel and downloads XLSX', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('pdf-to-excel');
    expect(body).toContain('"sheetMode":"tables"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'excel-job',
        operation: 'pdf-to-excel',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/excel-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'excel-job',
  operation: 'pdf-to-excel',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'report.xlsx',
    mediaType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    sizeBytes: 4,
    downloadUrl: '/api/v1/jobs/excel-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/excel-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.'
          + 'spreadsheetml.sheet',
        body: Buffer.from('PK\u0003\u0004'),
      });
    },
  );

  await page.goto('/pdf-to-excel');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 mock'),
  });
  await page.getByLabel('Worksheet layout').selectOption('tables');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert PDF to Excel',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report.xlsx');
  await expect(page.getByText(
    'Excel workbook download started!',
  )).toBeVisible();
});
