# Development

## Prerequisites

- Java 25
- Maven 3.9+
- Node.js 22+
- Docker with Compose

## Docker Compose

The default stack uses PostgreSQL and local streaming storage:

```bash
docker compose up --build
```

The backend's diagnostic port binds to `127.0.0.1:8080`; external traffic should use
the Nginx frontend proxy.

On first startup after upgrading, the backend entrypoint moves artifacts from the old
volume root into `legacy/`, creates the jobs layout, repairs ownership, and then drops
to the unprivileged application user.

For a clean milestone build:

```bash
docker compose build --no-cache
```

The optional SeaweedFS integration stack is available with:

```bash
docker compose --profile s3 up seaweed-master seaweed-volume seaweed-filer seaweed-s3
```

That profile is for local compatibility testing. Production must configure SeaweedFS
IAM separately and inject `PDF_STORAGE_S3_ACCESS_KEY` and
`PDF_STORAGE_S3_SECRET_KEY` through a secret store.

## Local backend

Start PostgreSQL first, then:

```bash
cd backend
DATABASE_URL=jdbc:postgresql://localhost:5432/pdftools \
DATABASE_USERNAME=pdftools \
DATABASE_PASSWORD=pdftools \
mvn spring-boot:run
```

Important settings:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PDF_STORAGE_TYPE` | `local` | `local` or `s3` |
| `PDF_STORAGE_LOCAL_ROOT` | `/tmp/pdf-storage/jobs` | Local object root |
| `PDF_JOB_WORK_ROOT` | `/tmp/pdf-work` | Ephemeral worker directories |
| `PDF_ENABLED_OPERATIONS` | stable operations | Comma-separated submission feature flags |
| `PDF_MULTIPART_TEMP_DIR` | `/tmp/pdf-multipart` | Private multipart spill directory; must be dedicated to one backend instance |
| `PDF_SECURITY_MAX_OUTPUT_BYTES` | `134217728` | Protect/Unlock output byte limit |
| `PDF_TO_JPG_MAX_SELECTED_PAGES` | `500` | Maximum pages rendered per conversion |
| `PDF_TO_JPG_MAX_DPI` | `300` | Maximum render resolution |
| `PDF_TO_JPG_MAX_PIXELS_PER_PAGE` | `20000000` | Per-page render pixel limit |
| `PDF_TO_JPG_MAX_WORKER_HEAP_BYTES` | `268435456` | Isolated renderer heap cap |
| `PDF_TO_JPG_WORKER_TIMEOUT` | `5m` | Isolated renderer wall-time cap |
| `JPG_TO_PDF_MAX_IMAGES` | `100` | Maximum JPEG inputs per conversion |
| `JPG_TO_PDF_MAX_PIXELS_PER_IMAGE` | `50000000` | Per-image pixel limit |
| `JPG_TO_PDF_MAX_OUTPUT_BYTES` | `134217728` | Generated PDF byte limit |
| `JPG_TO_PDF_VALIDATION_WORKER_HEAP_BYTES` | `134217728` | JPEG validator heap cap |
| `JPG_TO_PDF_VALIDATION_WORKER_TIMEOUT` | `2m` | JPEG validator wall-time cap |
| `WATERMARK_MAX_IMAGE_PIXELS` | `4000000` | Watermark image pixel limit |
| `WATERMARK_MAX_IMAGE_BYTES` | `10485760` | Watermark image byte limit |
| `WATERMARK_MAX_IMAGE_DIMENSION` | `4096` | Watermark image side limit |
| `EDIT_MAX_ELEMENTS` | `500` | Maximum elements in one edit plan |
| `EDIT_MAX_IMAGES` | `10` | Maximum uploaded edit images |
| `EDIT_MAX_IMAGE_BYTES` | `10485760` | Per-edit-image byte limit |
| `EDIT_MAX_TOTAL_DECODED_IMAGE_BYTES` | `67108864` | Aggregate decoded edit-image budget |
| `REDACT_MAX_AREAS` | `500` | Maximum redaction areas |
| `REDACT_MAX_AREAS_PER_PAGE` | `100` | Maximum areas on one page |
| `REDACT_MAX_DOCUMENT_PAGES` | `1000` | Maximum input pages |
| `REDACT_RENDER_DPI` | `200` | Sanitized raster resolution |
| `REDACT_JPEG_QUALITY` | `95` | Sanitized raster JPEG quality |
| `REDACT_MAX_PIXELS_PER_PAGE` | `20000000` | Pre-allocation page pixel limit |
| `REDACT_MAX_OUTPUT_BYTES` | `536870912` | Sanitized PDF byte limit |
| `REDACT_WORKER_HEAP_BYTES` | `268435456` | Isolated worker heap cap |
| `REDACT_WORKER_TIMEOUT` | `5m` | Isolated worker wall-time cap |
| `OFFICE_LIBREOFFICE_BINARY` | `soffice` | LibreOffice executable |
| `OFFICE_CONVERSION_MODE` | `queue` | Networkless sidecar or explicit `direct` mode |
| `OFFICE_QUEUE_REQUEST_ROOT` | `/var/lib/pdf-tools-office/requests` | Backend-write/sidecar-read requests |
| `OFFICE_QUEUE_RESPONSE_ROOT` | `/var/lib/pdf-tools-office/responses` | Sidecar-write/backend-read responses |
| `OFFICE_QUEUE_SIGNAL_ROOT` | `/var/lib/pdf-tools-office/signals` | Backend-write/sidecar-read signals |
| `OFFICE_QUEUE_WAIT_TIMEOUT` | `5m` | Queue wait before conversion starts |
| `OFFICE_QUEUE_RETENTION` | `1h` | Stale queue retention |
| `OFFICE_QUEUE_CLEANUP_INTERVAL` | `1m` | Queue cleanup cadence |
| `OFFICE_SIDECAR_WORK_ROOT` | `/tmp/office-work` | Size-limited sidecar scratch |
| `OFFICE_WORKER_USER` | `officeworker` | Native converter user |
| `OFFICE_MAX_INPUT_BYTES` | `52428800` | Office input byte limit |
| `OFFICE_MAX_EXPANDED_INPUT_BYTES` | `268435456` | Expanded OOXML limit |
| `OFFICE_MAX_ARCHIVE_ENTRIES` | `10000` | OOXML ZIP entry limit |
| `OFFICE_MAX_OUTPUT_BYTES` | `134217728` | Converted PDF byte limit |
| `OFFICE_MAX_LOG_BYTES` | `1048576` | Per-conversion log byte limit |
| `OFFICE_MAX_ADDRESS_SPACE_BYTES` | `1073741824` | Linux address-space cap |
| `OFFICE_CPU_TIME_SECONDS` | `120` | Native-process CPU limit |
| `OFFICE_MAX_OPEN_FILES` | `256` | Native-process file-descriptor limit |
| `OFFICE_MAX_WORKER_PROCESSES` | `96` | Worker-UID process limit |
| `OFFICE_WALL_TIMEOUT` | `2m` | Conversion wall-time cap |
| `EXCEL_MAX_SHEETS` | `100` | Workbook sheet limit |
| `EXCEL_MAX_USED_CELLS` | `1000000` | Used-range scan cell limit |
| `EXCEL_MAX_PREPARED_BYTES` | `104857600` | Prepared workbook byte limit |
| `HTML_QUEUE_REQUEST_ROOT` | `/var/lib/pdf-tools-html/requests` | Backend-write/browser-read requests |
| `HTML_QUEUE_RESPONSE_ROOT` | `/var/lib/pdf-tools-html/responses` | Browser-write/backend-read responses |
| `HTML_QUEUE_SIGNAL_ROOT` | `/var/lib/pdf-tools-html/signals` | Backend-write/browser-read signals |
| `HTML_QUEUE_WAIT_TIMEOUT` | `5m` | HTML queue wait before rendering starts |
| `HTML_QUEUE_RETENTION` | `1h` | Stale HTML queue retention |
| `HTML_WALL_TIMEOUT` | `1m` | Chromium render wall-time limit |
| `HTML_MAX_INPUT_BYTES` | `10485760` | UTF-8 HTML input byte limit |
| `HTML_MAX_OUTPUT_BYTES` | `67108864` | Rendered PDF byte limit |
| `HTML_MAX_PAGES` | `200` | Rendered PDF page limit |
| `PDF_STORAGE_S3_ENDPOINT` | none | SeaweedFS/S3 endpoint |
| `PDF_STORAGE_S3_BUCKET` | `pdf-tools` | Artifact bucket |

Roll out a new operation in two phases across multiple replicas: deploy the binary
everywhere with its key disabled, then add the key to `PDF_ENABLED_OPERATIONS`.
Workers only dispatch operation keys registered in their local binary.

Docker Compose starts `office-converter` with `network_mode: none`, a private PID
namespace, memory/CPU/PID limits, and three one-way queue volumes. The backend can
write requests/signals but only read responses; the sidecar has the inverse mounts.
For local Maven development on macOS, set `OFFICE_CONVERSION_MODE=direct`;
Seatbelt still denies IP networking and native limits remain active, but filesystem
reads are not isolated and only trusted local fixtures should be used. Linux direct
mode fails closed; use Docker Compose so conversion stays in the sidecar.

HTML rendering uses a separate Playwright sidecar with no network, a read-only
root filesystem, non-root Chromium sandboxing, the version-matched Playwright
seccomp profile, private tmpfs, and one-way queue volumes. HTML inputs must
contain required CSS, scripts, fonts, SVG, and images inline. Local paths and
external requests are blocked.

## Local frontend

```bash
cd frontend
npm ci
npm run dev
```

`VITE_JOB_API_URL` defaults to `/api/v1`. `VITE_API_URL` remains available while
legacy operation pages migrate.

## Validation

```bash
cd backend && mvn test
cd frontend && npm run lint && npm test && npm run build
cd frontend && npx playwright install chromium && npm run test:e2e
cd html-converter && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm ci --ignore-scripts && npm test
docker compose build --no-cache
```

Tests use generated or synthetic fixtures only. Do not add customer or personal data.
