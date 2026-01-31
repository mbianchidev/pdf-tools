# PDF Tools - Backend

A Java Spring Boot REST API for PDF manipulation operations.

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 3.2.1 | Application framework |
| Apache PDFBox | 3.0.1 | PDF manipulation |
| iText | 8.0.2 | Advanced PDF operations |
| Apache POI | 5.2.5 | DOCX generation |
| Java | 17 | Runtime |

## Features

### PDF Operations
- **Merge** - Combine multiple PDF files
- **Split** - Split into individual pages or custom groups
- **Extract** - Extract specific pages
- **Remove** - Remove specific pages
- **Watermark** - Add text watermarks with positioning
- **Add Text** - Add text with fonts and colors
- **Add Signature** - Add signature images
- **Redact** - Add redaction boxes
- **Convert to Markdown** - Extract text as Markdown
- **Convert to DOCX** - Convert to Word document

## Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Running Locally

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run
```

The API will be available at http://localhost:8080

### Running with JAR

```bash
mvn clean package
java -jar target/pdf-tools-backend-1.0.0.jar
```

## API Endpoints

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
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile |
| `PDF_UPLOAD_DIR` | `/tmp/pdf-uploads` | Temp file storage |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:80` | Allowed CORS origins |

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

# Run with coverage
mvn test jacoco:report
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
