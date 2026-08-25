import { readFile, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const CONTENT_SECURITY_POLICY = [
  "default-src 'none'",
  "img-src data: blob:",
  "font-src data:",
  "style-src 'unsafe-inline'",
  "script-src 'unsafe-inline'",
  "connect-src 'none'",
  "frame-src 'none'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
  "worker-src 'none'",
].join('; ');

export const injectCsp = (html) => {
  const meta = `<meta http-equiv="Content-Security-Policy" content="${CONTENT_SECURITY_POLICY}">`;
  return /<head(?:\s[^>]*)?>/i.test(html)
    ? html.replace(/<head(?:\s[^>]*)?>/i, (head) => `${head}${meta}`)
    : `${meta}${html}`;
};

export const isAllowedResource = (url) => {
  try {
    return ['about:', 'blob:', 'data:'].includes(new URL(url).protocol);
  } catch {
    return false;
  }
};

export const pdfOptions = (plan, outputPath) => ({
  path: outputPath,
  format: plan.pageSize,
  landscape: plan.landscape,
  printBackground: plan.printBackground,
  margin: {
    top: `${plan.marginMm}mm`,
    right: `${plan.marginMm}mm`,
    bottom: `${plan.marginMm}mm`,
    left: `${plan.marginMm}mm`,
  },
  preferCSSPageSize: false,
  displayHeaderFooter: false,
  tagged: true,
  outline: false,
});

export const convert = async ({
  sourcePath,
  outputPath,
  plan,
  timeoutMs,
}) => {
  const source = await readFile(sourcePath);
  const html = new TextDecoder('utf-8', { fatal: true }).decode(source);
  if (html.includes('\u0000')) {
    throw codedError(
      'INVALID_HTML_DOCUMENT',
      'The file is not a readable HTML document',
    );
  }

  let browser;
  try {
    browser = await chromium.launch({
      headless: true,
      chromiumSandbox: true,
      args: [
        '--disable-background-networking',
        '--disable-breakpad',
        '--disable-component-update',
        '--disable-default-apps',
        '--disable-extensions',
        '--disable-features=MediaRouter,OptimizationHints',
        '--disable-sync',
        '--metrics-recording-only',
        '--no-first-run',
      ],
    });
    const context = await browser.newContext({
      acceptDownloads: false,
      offline: true,
      serviceWorkers: 'block',
    });
    await context.route('**/*', async (route) => {
      if (isAllowedResource(route.request().url())) {
        await route.continue();
      } else {
        await route.abort('blockedbyclient');
      }
    });
    const page = await context.newPage();
    page.setDefaultTimeout(timeoutMs);
    page.setDefaultNavigationTimeout(timeoutMs);
    page.on('dialog', (dialog) => dialog.dismiss());
    page.on('popup', (popup) => popup.close());
    await page.emulateMedia({ media: 'print' });
    await page.setContent(injectCsp(html), {
      waitUntil: 'load',
      timeout: timeoutMs,
    });
    await page.evaluate(async () => {
      if (document.fonts) await document.fonts.ready;
    });
    await page.pdf(pdfOptions(plan, outputPath));
    await context.close();
  } catch (error) {
    if (error?.code) throw error;
    throw codedError(
      'HTML_RENDER_FAILED',
      'Chromium could not render the HTML document',
      error,
    );
  } finally {
    await browser?.close().catch(() => {});
  }
};

const codedError = (code, message, cause) => Object.assign(
  new Error(message, { cause }),
  { code },
);

const main = async () => {
  if (process.argv.length !== 6) {
    throw codedError(
      'HTML_RENDER_PROTOCOL_ERROR',
      'The HTML renderer received invalid arguments',
    );
  }
  const [, , sourcePath, outputPath, errorPath, optionsJson] = process.argv;
  try {
    const plan = JSON.parse(optionsJson);
    await convert({
      sourcePath,
      outputPath,
      plan,
      timeoutMs: numberEnvironment('HTML_BROWSER_TIMEOUT_MS', 45_000),
    });
  } catch (error) {
    console.error(error?.stack ?? error);
    if (error?.cause) {
      console.error(error.cause?.stack ?? error.cause);
    }
    await writeFile(errorPath, JSON.stringify({
      code: /^HTML_[A-Z0-9_]{1,91}$/.test(error?.code)
        ? error.code
        : 'HTML_RENDER_FAILED',
      message: typeof error?.message === 'string'
        ? error.message.slice(0, 1000)
        : 'Chromium could not render the HTML document',
    }), { encoding: 'utf8', mode: 0o600 });
    process.exitCode = 2;
  }
};

const numberEnvironment = (name, fallback) => {
  const parsed = Number.parseInt(process.env[name] ?? '', 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
};

if (process.argv[1] && import.meta.url === new URL(
  `file://${process.argv[1]}`,
).href) {
  await main();
}
