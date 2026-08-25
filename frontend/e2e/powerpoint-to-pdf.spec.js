import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('converts a presentation and downloads one PDF', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('powerpoint-to-pdf');
    expect(body).toContain('slides.pptx');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'powerpoint-job',
        operation: 'powerpoint-to-pdf',
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
  operation: 'powerpoint-to-pdf',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'slides.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
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
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/powerpoint-to-pdf');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'slides.pptx',
    mimeType: 'application/vnd.openxmlformats-officedocument.'
      + 'presentationml.presentation',
    buffer: Buffer.from('mock PPTX'),
  });

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', {
    name: 'Convert PowerPoint to PDF',
  }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('slides.pdf');
  await expect(page.getByText(
    'Converted presentation download started!',
  )).toBeVisible();
});
