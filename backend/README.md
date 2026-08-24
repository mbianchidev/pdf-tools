# PDF Tools - Backend

A Java Spring Boot REST API for PDF manipulation operations.

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 4.1 | Application framework |
| Apache PDFBox | 3.0.8 | PDF manipulation |
| PostgreSQL | 18 | Job metadata |
| Flyway | managed | Schema migrations |
| AWS SDK S3 | 2.54 | SeaweedFS-compatible artifact storage |
| Apache POI | 5.5 | Office document generation |
| Java | 25 | Runtime |

## Features

### PDF Operations
- **Merge** - Combine multiple PDF files
- **Split** - Split into individual pages or custom groups
- **Extract** - Extract specific pages
- **Remove** - Remove strict page ranges while retaining at least one page
- **Rotate** - Apply shared or independent page rotations
- **Organize** - Reorder, rotate, duplicate, and omit pages in one plan
- **Crop** - Apply normalized shared or per-page crop boxes
- **Page Numbers** - Number ranges with templates, fonts, and positions
- **Protect** - AES-256 password encryption and explicit permissions
- **Unlock** - Authenticated password removal with structure preservation
- **PDF to JPG** - Bounded page rendering with DPI and quality controls
- **JPG to PDF** - Ordered raw JPEG embedding with paper controls
- **Watermark** - Styled text/image overlays on selected pages
- **Add Text** - Add text with fonts and colors
- **Add Signature** - Add signature images
- **Redact** - Add redaction boxes
- **Convert to Markdown** - Extract text as Markdown
- **Convert to DOCX** - Convert to Word document

## Getting Started

### Prerequisites
- Java 25 or higher
- Maven 3.9+
- PostgreSQL 18

### Running Locally

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run
```

The API will be available at http://localhost:8080. Configure PostgreSQL through
`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

### Running with JAR

```bash
mvn clean package
java -jar target/pdf-tools-backend-1.0.0.jar
```

## API Endpoints

Versioned tools use the asynchronous jobs API documented in
[`docs/api-v1.md`](../docs/api-v1.md):

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/jobs` | Validate, persist, and queue an operation |
| GET | `/api/v1/jobs/{jobId}` | Read status and `outputs[]` |
| GET | `/api/v1/jobs/{jobId}/events` | Stream SSE progress |
| DELETE | `/api/v1/jobs/{jobId}` | Request cancellation |
| GET | `/api/v1/jobs/{jobId}/outputs/{outputId}` | Stream an artifact |

The Docker deployment enables `merge`, `split`, `remove`, `rotate`, `organize`, `crop`, `page-numbers`, `protect`, `unlock`, `pdf-to-jpg`, `jpg-to-pdf`, and `watermark`. Their contracts and fidelity limits are
documented in [`docs/operations/merge.md`](../docs/operations/merge.md) and
[`docs/operations/split.md`](../docs/operations/split.md), with Remove Pages documented
in [`docs/operations/remove.md`](../docs/operations/remove.md).
Rotate options are documented in [`docs/operations/rotate.md`](../docs/operations/rotate.md).
Organize plans are documented in [`docs/operations/organize.md`](../docs/operations/organize.md).
Crop coordinates are documented in [`docs/operations/crop.md`](../docs/operations/crop.md).
Page numbering is documented in [`docs/operations/page-numbers.md`](../docs/operations/page-numbers.md).
Protection and sensitive option storage are documented in [`docs/operations/protect.md`](../docs/operations/protect.md).
Password removal is documented in [`docs/operations/unlock.md`](../docs/operations/unlock.md).
Raster conversion is documented in [`docs/operations/pdf-to-jpg.md`](../docs/operations/pdf-to-jpg.md).
Image assembly is documented in [`docs/operations/jpg-to-pdf.md`](../docs/operations/jpg-to-pdf.md).
Watermark styling is documented in [`docs/operations/watermark.md`](../docs/operations/watermark.md).

The following legacy endpoints remain during migration:

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/pdf/merge` | Merge multiple PDFs |
| POST | `/api/pdf/split` | Split PDF into pages |
| POST | `/api/pdf/extract` | Extract specific pages |
| POST | `/api/pdf/remove` | Remove specific pages |
| POST | `/api/pdf/watermark` | Add text watermark |
| POST | `/api/pdf/add-text` | Add text overlay |
| POST | `/api/pdf/add-signature` | Add signature image |
| POST | `/api/pdf/redact` | Add redaction boxes |
| POST | `/api/pdf/convert/markdown` | Convert to Markdown |
| POST | `/api/pdf/convert/docx` | Convert to DOCX |
| GET | `/api/pdf/download/{filename}` | Download processed file |
| GET | `/api/pdf/health` | Health check |

### Request Examples

#### Merge PDFs
```bash
curl -X POST http://localhost:8080/api/pdf/merge \
  -F "files=@file1.pdf" \
  -F "files=@file2.pdf"
```

#### Add Watermark
```bash
curl -X POST http://localhost:8080/api/pdf/watermark \
  -F "file=@document.pdf" \
  -F "text=CONFIDENTIAL" \
  -F "rotation=45" \
  -F "opacity=0.3"
```

#### Add Text
```bash
curl -X POST http://localhost:8080/api/pdf/add-text \
  -F "file=@document.pdf" \
  -F "text=Hello World" \
  -F "x=100" \
  -F "y=700" \
  -F "page=1" \
  -F "fontSize=14" \
  -F "fontName=HELVETICA" \
  -F "fontColor=#000000"
```

## Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/pdftools/
│   │   │   ├── PdfToolsApplication.java  # Main entry point
│   │   │   ├── config/
│   │   │   │   └── WebConfig.java        # CORS configuration
│   │   │   ├── controller/
│   │   │   │   └── PdfController.java    # REST endpoints
│   │   │   ├── service/
│   │   │   │   └── PdfService.java       # Business logic
│   │   │   ├── dto/
│   │   │   │   └── PdfOperationResult.java
│   │   │   └── exception/
│   │   │       ├── PdfProcessingException.java
│   │   │       └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/pdftools/
│           └── service/
│               └── PdfServiceTest.java
├── Dockerfile
└── pom.xml
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Server port
server.port=8080

# File upload limits
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB

# Upload directory
pdf.upload.dir=/tmp/pdf-uploads

# CORS
cors.allowed-origins=http://localhost:80,http://localhost:3000
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/pdftools` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `pdftools` | PostgreSQL user |
| `DATABASE_PASSWORD` | `pdftools` | PostgreSQL password |
| `PDF_STORAGE_TYPE` | `local` | `local` or `s3` artifact storage |
| `PDF_STORAGE_LOCAL_ROOT` | `/tmp/pdf-storage/jobs` | Local artifact root |
| `PDF_JOB_WORK_ROOT` | `/tmp/pdf-work` | Isolated worker root |
| `PDF_ENABLED_OPERATIONS` | completed stable operations | Comma-separated submission feature flags |
| `PDF_OPTIONS_ENCRYPTION_KEY` | generated local key | Base64 32-byte shared key for sensitive job options |
| `PDF_SECURITY_MAX_OUTPUT_BYTES` | `134217728` | Protect/Unlock output byte limit |
| `PDF_TO_JPG_MAX_SELECTED_PAGES` | `500` | Maximum pages rendered per job |
| `PDF_TO_JPG_MAX_DPI` | `300` | Maximum requested render resolution |
| `PDF_TO_JPG_MAX_PIXELS_PER_PAGE` | `20000000` | Pre-allocation page pixel limit |
| `PDF_TO_JPG_MAX_WORKER_HEAP_BYTES` | `268435456` | Isolated renderer heap cap |
| `PDF_TO_JPG_WORKER_TIMEOUT` | `5m` | Isolated renderer wall-time cap |
| `JPG_TO_PDF_MAX_IMAGES` | `100` | Maximum ordered JPEG inputs |
| `JPG_TO_PDF_MAX_PIXELS_PER_IMAGE` | `50000000` | Per-image pixel limit |
| `JPG_TO_PDF_MAX_OUTPUT_BYTES` | `134217728` | Generated PDF byte limit |
| `JPG_TO_PDF_VALIDATION_WORKER_HEAP_BYTES` | `134217728` | JPEG validator heap cap |
| `JPG_TO_PDF_VALIDATION_WORKER_TIMEOUT` | `2m` | JPEG validator wall-time cap |
| `WATERMARK_MAX_IMAGE_PIXELS` | `4000000` | Watermark image pixel limit |
| `CORS_ALLOWED_ORIGINS` | local frontend origins | Allowed CORS origins |

For a rolling deployment, first deploy new code to every worker while leaving new
operation keys out of `PDF_ENABLED_OPERATIONS`. Enable a new key only after every
worker can execute it. Dispatchers ignore queued operations not registered in their
local binary.

## Docker

Build and run with Docker:

```bash
docker build -t pdf-tools-backend .
docker run -p 8080:8080 pdf-tools-backend
```

## Testing

```bash
# Run all tests
mvn test

# Compile without tests
mvn -DskipTests compile
```

## Dependencies

Key dependencies from `pom.xml`:

```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- PDF Libraries -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>

<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext-core</artifactId>
    <version>8.0.2</version>
</dependency>

<!-- DOCX Generation -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

## Error Handling

The API returns structured error responses:

```json
{
  "success": false,
  "message": "Error description",
  "outputFilename": null
}
```

Common HTTP status codes:
- `200` - Success
- `400` - Bad request (invalid parameters)
- `500` - Internal server error (processing failed)

## Health Check

```bash
curl http://localhost:8080/api/pdf/health
```

Returns:
```json
{
  "status": "UP",
  "message": "PDF Tools Backend is running"
}
```
