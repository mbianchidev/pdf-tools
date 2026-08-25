import { expect, test } from '@playwright/test';

test('configures PDF-to-Word and downloads one DOCX', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('pdf-to-word');
    expect(body).toContain('"mode":"editable"');
    expect(body).toContain('"includeImages":true');
    expect(body).toContain('"detectTables":false');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'word-job',
        operation: 'pdf-to-word',
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
  operation: 'pdf-to-word',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'report.docx',
    mediaType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    sizeBytes: 4,
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
        contentType: 'application/vnd.openxmlformats-officedocument.'
          + 'wordprocessingml.document',
        body: Buffer.from('PK\u0003\u0004'),
      });
    },
  );

  await page.goto('/pdf-to-word');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'report.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 mock'),
  });
  await page.getByLabel('Detect aligned tables').uncheck();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert PDF to Word',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('report.docx');
  await expect(page.getByText(
    'Word document download started!',
  )).toBeVisible();
});
