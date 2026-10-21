# PDF Tools

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue.svg)](https://react.dev/)

A self-hosted web application for organizing, editing, securing, converting, repairing,
and comparing PDF documents. Use a browser workspace while keeping document processing
on infrastructure you control, rather than sending files to a third-party PDF service.

<img width="1325" height="804" alt="PDF Tools interface" src="https://github.com/user-attachments/assets/452afb5a-57c0-403b-bbbb-86c211f48ddb" />

React 19 and Vite 8 power the frontend. Java 25, Spring Boot 4.1, PDFBox, and Apache
POI provide document processing, with PostgreSQL 18 and Flyway for job metadata.
Artifacts use local streaming storage by default or an S3-compatible service such as
SeaweedFS.

## Implemented tools

| Workflow | Capabilities |
| --- | --- |
| Organize | Merge, split, extract, remove, rotate, reorder, duplicate, and crop pages |
| Mark up | Add text, images, shapes, highlights, notes, page numbers, watermarks, and signature images |
| Secure | Password protection, unlocking with a known password, and permanent rasterized redaction |
| Convert | Word, PowerPoint, Excel, and JPG to/from PDF; self-contained HTML to PDF; PDF to Markdown and validated PDF/A-1b, PDF/A-2b, or PDF/A-3b |
| Optimize and inspect | Compress files, attempt structural repair with qpdf, and compare text, layout, or rendered pages |

Most tools use asynchronous, cancellable jobs with progress updates and streaming
downloads. Extract pages, add/edit text, and signatures retain the legacy `/api/pdf`
API. See the [operation guides](docs/README.md#operation-guides) for supported inputs,
options, and fidelity limits.

## Quick start

### Prerequisites

- Git and Docker with Compose v2 or later, configured to run Linux containers.
  macOS and Windows hosts need a Linux container VM.
- The runtime must support Linux capability controls, CPU/memory/PID limits, and
  custom seccomp profiles for the converter sidecars. A compatible Docker CLI
  alone is not sufficient.
- `curl` for the optional health-check and API examples.
- Available host ports **80** for the frontend and **127.0.0.1:8080** for backend
  diagnostics.
- Internet access for the initial image pulls and dependency downloads.

The Docker builds supply Java, Node.js, LibreOffice, qpdf, and Chromium; you do not
need to install them on the host. The stack includes PostgreSQL, the API, an Nginx
frontend, and separate networkless Office and HTML converter sidecars.

### Build and run

```bash
git clone https://github.com/mbianchidev/pdf-tools.git
cd pdf-tools
docker compose build --no-cache
docker compose up -d
```

Wait for the services to become healthy, then open <http://localhost>. Check startup
and backend readiness with:

```bash
docker compose ps
curl --fail http://localhost:8080/api/pdf/health
```

The health endpoint should return HTTP 200. The `startup-message` container prints
`PDF Tools is ready!` and exits normally after the frontend becomes healthy. If a
service fails to start, inspect its logs:

```bash
docker compose logs --tail=100 backend office-converter html-converter
```

Stop the stack with `docker compose down`. Named volumes retain the database, stored
artifacts, and generated options-encryption key across restarts. Adding `--volumes`
deletes that persisted data.

## Usage

1. Open <http://localhost/new>, load a PDF, and choose a workflow. The workflow
   topbar carries the source file between compatible PDF tools. For Office, JPG,
   or HTML input, open the corresponding conversion tool from the landing page.
2. Configure the operation and run it. Job-based tools display progress and support
   cancellation.
3. Download the resulting document or bundle. **v1 job inputs and outputs expire
   two hours after completion, failure, or cancellation**, so save results locally.

For automation, submit multipart requests through the frontend proxy:

```bash
curl --fail http://localhost/api/v1/jobs \
  -F 'operation=merge' \
  -F 'options={"outputFilename":"merged.pdf"}' \
  -F 'files=@first.pdf' \
  -F 'files=@second.pdf'
```

Replace the example filenames with two PDFs. A successful submission returns
`202 Accepted` and a job ID. Poll `GET /api/v1/jobs/{jobId}` until its status is
`COMPLETED`, `FAILED`, or `CANCELLED`. Completed jobs expose downloadable entries in
`outputs[]` with a `downloadUrl`. The
[Jobs API v1 reference](docs/api-v1.md) covers progress events, cancellation, errors,
and operation options.

## Configuration

Compose reads the interpolated settings in [docker-compose.yml](docker-compose.yml)
from your shell or an untracked `.env` file in the repository root. Re-run
`docker compose up -d` after changing them.

| Setting | Default and purpose |
| --- | --- |
| `PDF_STORAGE_TYPE` | `local`; set to `s3` to use S3-compatible artifact storage |
| `PDF_STORAGE_LOCAL_ROOT` | `/tmp/pdf-storage/jobs` inside the backend's persistent `pdf-storage` volume |
| `PDF_STORAGE_S3_ENDPOINT`, `PDF_STORAGE_S3_BUCKET` | Endpoint is unset; bucket defaults to `pdf-tools`. Configure these and the S3 credentials when selecting `s3` |
| `PDF_STORAGE_S3_ACCESS_KEY`, `PDF_STORAGE_S3_SECRET_KEY` | Unset; inject deployment credentials rather than committing them |
| `PDF_STORAGE_S3_CREATE_BUCKET` | `false`; provision the bucket separately or explicitly enable creation |
| `PDF_ENABLED_OPERATIONS` | Compose enables the implemented v1 operation list; a standalone backend enables a smaller subset |
| `PDF_OPTIONS_ENCRYPTION_KEY` | If unset, the backend generates and persists a private key. Supply a stable Base64-encoded 32-byte key for shared deployments to encrypt sensitive job options |

The optional `s3` Compose profile starts SeaweedFS for compatibility testing; it
does **not** switch the backend to S3 automatically. Production SeaweedFS needs its
own IAM configuration and private endpoint. See the
[development guide](docs/development.md) for S3 setup, operation limits, and the full
configuration reference.

## Important limitations

- **Self-hosted does not mean browser-only.** Files are uploaded to your server.
  The supplied Compose configuration uses development database credentials and
  exposes the frontend on port 80. Add TLS, authentication/authorization, and abuse
  controls before exposing it publicly; replace the database credentials in the
  deployment configuration. See the [security policy](SECURITY.md).
- **Uploads are bounded.** The default multipart limit is 100 MB for the entire
  request, including all files and metadata. Individual operations enforce
  additional byte, page, image, memory, and time limits.
- **Conversion is not lossless reconstruction.** PDF-to-Office and Markdown
  extraction is best effort, with no bundled OCR. Complex layouts and scanned
  documents may require preprocessing or manual correction. Visual conversion
  modes preserve pages as images, not editable text.
- **Redaction and signatures have distinct tradeoffs.** Secure redaction rebuilds
  every page as a raster image, removing searchable text, vectors, forms, and
  accessibility structure. Adding a signature image is not certificate-based
  digital signing.
- **HTML must be self-contained.** The isolated converter cannot fetch remote
  pages, stylesheets, fonts, images, or local files; include required assets inline.
- **Repair and archival validation have limits.** qpdf repair cannot guarantee
  recovery of damaged content. PDF/A conformance is validated with veraPDF, but
  conversion can change layout and does not guarantee visual or semantic parity.

## Development and documentation

For native development, use Java 25, Maven 3.9+, Node.js 22.12+, and PostgreSQL 18.
Native conversion tools and sidecar setup are also required for the relevant
operations; running only the API and frontend does not provide the complete stack.
Follow the [development guide](docs/development.md) for setup and validation commands.

- [Documentation index and operation guides](docs/README.md)
- [Architecture and processing boundaries](docs/architecture.md)
- [Jobs API v1](docs/api-v1.md)
- [Product principles](docs/product.md)
- [Design system](docs/design.md)
- [Backend guide](backend/README.md)
- [Frontend guide](frontend/README.md)
- [HTML converter sidecar](html-converter/README.md)

## Contributing and license

Contributions are welcome. Use the [development guide](docs/development.md) for the
current toolchain and the [contributing guide](CONTRIBUTING.md) for coding and pull
request conventions. Follow the [Code of Conduct](CODE_OF_CONDUCT.md), use
[Support](SUPPORT.md) for usage questions, and report vulnerabilities privately as
described in the [security policy](SECURITY.md).

PDF Tools is released under the [MIT License](LICENSE).
