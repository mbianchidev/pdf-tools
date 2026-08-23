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
| `PDF_MULTIPART_TEMP_DIR` | `/tmp/pdf-multipart` | Multipart spill directory |
| `PDF_STORAGE_S3_ENDPOINT` | none | SeaweedFS/S3 endpoint |
| `PDF_STORAGE_S3_BUCKET` | `pdf-tools` | Artifact bucket |

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
