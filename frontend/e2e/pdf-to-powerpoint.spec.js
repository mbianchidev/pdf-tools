import { expect, test } from '@playwright/test';

test('configures PDF-to-PowerPoint and downloads PPTX', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('pdf-to-powerpoint');
    expect(body).toContain('"mode":"editable"');
    expect(body).toContain('"slideSize":"widescreen"');
    expect(body).toContain('"detectTables":false');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'powerpoint-job',
        operation: 'pdf-to-powerpoint',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route(
    '**/api/v1/jobs/powerpoint-job/events',
    async (route) => {
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache',
        },
        body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'powerpoint-job',
  operation: 'pdf-to-powerpoint',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'slides.pptx',
    mediaType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    sizeBytes: 4,
    downloadUrl: '/api/v1/jobs/powerpoint-job/outputs/output-1',
  }],
})}

`,
      });
    },
  );
  await page.route(
    '**/api/v1/jobs/powerpoint-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.'
          + 'presentationml.presentation',
        body: Buffer.from('PK\u0003\u0004'),
      });
    },
  );

  await page.goto('/pdf-to-powerpoint');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'slides.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 mock'),
  });
  await page.getByLabel('Slide size').selectOption('widescreen');
  await page.getByLabel('Detect aligned tables').uncheck();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert PDF to PowerPoint',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('slides.pptx');
  await expect(page.getByText(
    'PowerPoint download started!',
  )).toBeVisible();
});
