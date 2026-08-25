# HTML Converter Sidecar

This image consumes the HTML conversion queue and renders self-contained UTF-8
documents with Playwright and Chromium.

```bash
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm ci --ignore-scripts
npm test
```

The runtime image is pinned to the same Playwright version as `package.json`.
Chromium runs as UID 10001 with its internal sandbox enabled. Docker Compose
adds a networkless namespace, read-only root, private tmpfs, cgroup limits,
`no-new-privileges`, and one-way queue mounts.

`seccomp_profile.json` is based on the Playwright v1.62.1 Docker profile from
<https://github.com/microsoft/playwright/blob/v1.62.1/utils/docker/seccomp_profile.json>.
It includes Chromium's user-namespace calls plus current glibc syscall
fallbacks and is distributed under Playwright's Apache-2.0 license.
