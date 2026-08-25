import { expect, test } from '@playwright/test';

test('compares text layout and rendered pages', async ({ page }) => {
  await page.route('**/api/v1/jobs', async (route) => {
    const body = route.request().postDataBuffer().toString('utf8');
    expect(body.indexOf('baseline.pdf')).toBeLessThan(
      body.indexOf('candidate.pdf'),
    );
    expect(body).toContain('compare');
    expect(body).toContain('"renderDpi":144');
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'compare-job',
        operation: 'compare',
        status: 'PENDING',
        version: 0,
        progress: 0,
        message: 'Queued',
        cancellationRequested: false,
        outputs: [],
      }),
    });
  });
  await page.route('**/api/v1/jobs/compare-job/events', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
      body: `event: job
id: 1
data: ${JSON.stringify({
  id: 'compare-job',
  operation: 'compare',
  status: 'COMPLETED',
  version: 1,
  progress: 100,
  message: 'Completed',
  cancellationRequested: false,
  outputs: [{
    id: 'archive-output',
    filename: 'baseline-vs-candidate-comparison.zip',
    mediaType: 'application/zip',
    sizeBytes: 100,
    downloadUrl: '/api/v1/jobs/compare-job/outputs/archive-output',
  }, {
    id: 'report-output',
    filename: 'baseline-vs-candidate-comparison-report.json',
    mediaType: 'application/json',
    sizeBytes: 200,
    downloadUrl: '/api/v1/jobs/compare-job/outputs/report-output',
  }],
})}

`,
    });
  });
  await page.route(
    '**/api/v1/jobs/compare-job/outputs/archive-output',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/zip',
        body: Buffer.from('PK\u0003\u0004'),
      });
    },
  );
  await page.route(
    '**/api/v1/jobs/compare-job/outputs/report-output',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'different',
          summary: {
            baselinePages: 1,
            candidatePages: 1,
            comparedPages: 1,
            textChangedPages: 1,
            layoutChangedPages: 1,
            visualChangedPages: 1,
            totalAddedLines: 1,
            totalRemovedLines: 1,
            maxVisualDifferencePercent: 4.25,
          },
          pages: [{
            page: 1,
            baselinePresent: true,
            candidatePresent: true,
            text: {
              changed: true,
              addedLines: 1,
              removedLines: 1,
              changes: [{
                type: 'removed',
                text: 'Revenue 100',
              }, {
                type: 'added',
                text: 'Revenue 120',
              }],
            },
            layout: { changed: true, movedTextLines: 1 },
            visual: {
              changed: true,
              differencePercent: 4.25,
              diffImage: 'visual/page-001-diff.png',
            },
          }],
        }),
      });
    },
  );

  await page.goto('/compare');
  const inputs = page.locator('input[type="file"]');
  await inputs.nth(0).setInputFiles({
    name: 'baseline.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 baseline'),
  });
  await inputs.nth(1).setInputFiles({
    name: 'candidate.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7 candidate'),
  });
  await page.getByLabel('Render resolution').selectOption('144');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Compare PDFs' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe(
    'baseline-vs-candidate-comparison.zip',
  );
  await expect(page.getByText('Documents differ')).toBeVisible();
  await expect(page.getByText('Revenue 100')).toBeVisible();
  await expect(page.getByText('Revenue 120')).toBeVisible();
});
