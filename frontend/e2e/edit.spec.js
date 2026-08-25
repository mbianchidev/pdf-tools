import { expect, test } from '@playwright/test';
import { FOUR_PAGE_PDF } from './fixtures';

test('submits text shapes and annotations in one edit plan', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body).toContain('edit');
    expect(body).toContain('"type":"text"');
    expect(body).toContain('"type":"rectangle"');
    expect(body).toContain('"type":"note"');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'edit-job',
        operation: 'edit',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/edit-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'edit-job',
  operation: 'edit',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'output-1',
    filename: 'source_edited.pdf',
    mediaType: 'application/pdf',
    sizeBytes: FOUR_PAGE_PDF.length,
    downloadUrl: '/api/v1/jobs/edit-job/outputs/output-1',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/edit-job/outputs/output-1',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: FOUR_PAGE_PDF,
      });
    },
  );

  await page.goto('/edit');
  await page.locator('input[type="file"]').first().setInputFiles({
    name: 'source.pdf',
    mimeType: 'application/pdf',
    buffer: FOUR_PAGE_PDF,
  });
  const canvas = page.locator('.edit-preview__page');
  await page.getByRole('button', { name: 'Add text' }).click();
  await canvas.click({ position: { x: 300, y: 120 } });
  await page.getByRole('textbox', { name: 'Text', exact: true })
    .fill('Reviewed');
  await page.getByRole('button', { name: 'Add rectangle' }).click();
  await canvas.click({ position: { x: 160, y: 300 } });
  await page.getByRole('button', { name: 'Add note' }).click();
  await canvas.click({ position: { x: 160, y: 300 } });
  await page.getByLabel('Contents').fill('Check this');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Apply edits & Download' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('source_edited.pdf');
  await expect(page.getByText('Edited PDF download started!')).toBeVisible();
});
