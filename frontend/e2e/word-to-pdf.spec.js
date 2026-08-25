import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('converts a Word document and downloads one PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('word-to-pdf');
    expect(body).toContain('report.docx');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'word-job',
        operation: 'word-to-pdf',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/word-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'word-job',
  operation: 'word-to-pdf',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'report.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/word-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/word-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/word-to-pdf');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.docx',
    mimeType: 'application/vnd.openxmlformats-officedocument.'
      + 'wordprocessingml.document',
    buffer: Buffer.from('mock DOCX'),
  });

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Convert Word to PDF' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report.pdf');
  await expect(page.getByText(
    'Converted PDF download started!',
  )).toBeVisible();
});
