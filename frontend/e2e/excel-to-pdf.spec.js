import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('configures an Excel conversion and downloads one PDF', async ({
  page,
}) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('excel-to-pdf');
    expect(body).toContain('"printAreaMode":"used"');
    expect(body).toContain('"orientation":"landscape"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'excel-job',
        operation: 'excel-to-pdf',
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
  operation: 'excel-to-pdf',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'workbook.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
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
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/excel-to-pdf');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'workbook.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.'
      + 'spreadsheetml.sheet',
    buffer: Buffer.from('mock XLSX'),
  });
  await page.getByLabel('Print area mode').selectOption('used');
  await page.getByLabel('Page orientation').selectOption('landscape');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert Excel to PDF',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('workbook.pdf');
  await expect(page.getByText(
    'Converted workbook download started!',
  )).toBeVisible();
});
