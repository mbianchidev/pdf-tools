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
| `PDF_STORAGE_S3_ENDPOINT` | none | SeaweedFS/S3 endpoint |
| `PDF_STORAGE_S3_BUCKET` | `pdf-tools` | Artifact bucket |

Roll out a new operation in two phases across multiple replicas: deploy the binary
everywhere with its key disabled, then add the key to `PDF_ENABLED_OPERATIONS`.
Workers only dispatch operation keys registered in their local binary.

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
docker compose build --no-cache
```

Tests use generated or synthetic fixtures only. Do not add customer or personal data.
