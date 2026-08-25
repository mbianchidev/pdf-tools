# HTML to PDF

Operation key: `html-to-pdf`

HTML to PDF renders one self-contained UTF-8 `.html` or `.htm` document with
Playwright and Chromium. Inline CSS, inline scripts, SVG, and `data:`/`blob:`
assets are supported. External URLs, local files, frames, objects, workers,
forms, and outbound connections are blocked.

## Options

```json
{
  "pageSize": "a4",
  "orientation": "portrait",
  "printBackground": true,
  "marginMm": 10,
  "outputFilename": "document.pdf"
}
```

- `pageSize` is `a4` (default), `letter`, or `legal`.
- `orientation` is `portrait` (default) or `landscape`.
- `printBackground` defaults to `true`.
- `marginMm` is a whole number from 0 through 50 and defaults to 10.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

The input is limited to 10 MiB and must decode as strict UTF-8 without null
characters. Output is limited to 64 MiB and 200 pages. Conversion has a
one-minute wall-time limit and a 1 MiB log limit.

## Browser isolation

The browser does not run in the API container. A dedicated sidecar uses a
version-pinned Playwright image and one-way request, response, and signal
volumes. The sidecar has:

- no network namespace connection;
- a read-only root filesystem;
- a dedicated non-root UID;
- Chromium's internal sandbox enabled;
- Playwright's version-matched seccomp profile;
- all Linux capabilities dropped except `SYS_CHROOT`, which Chromium needs for
  its internal jail;
- `no-new-privileges`;
- private tmpfs home and work directories;
- explicit memory, CPU, PID, shared-memory, input, output, log, and time limits.

Each job runs in a detached process group. Cancellation or timeout sends
`SIGTERM`, escalates to `SIGKILL`, waits for terminal queue state, and removes
the request, response, signals, browser profile, logs, and scratch files.
`dumb-init` reaps orphaned browser descendants.

The converter injects an additional restrictive Content Security Policy and
aborts non-`about:`, non-`data:`, and non-`blob:` requests in Playwright.
Chromium also runs offline inside a container with `network_mode: none`.

## Fidelity

Output follows Chromium's print engine, not a full interactive browser session.
Screen-only layout, unsupported print CSS, timing-dependent scripts, videos,
audio, plugins, remote fonts, and external assets do not carry over. CSS page
size is overridden by the requested paper controls. No browser screenshot or
commercial HTML-rendering parity is claimed.
