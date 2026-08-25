# PDF Tools - Agent Navigation Guide

This document provides comprehensive project information for AI agents to efficiently navigate and work with this codebase.

## Project Overview

**PDF Tools** is a full-stack web application for PDF manipulation with a React frontend and Java Spring Boot backend.

| Aspect | Details |
|--------|---------|
| **Type** | Full-stack web application |
| **Frontend** | React 19 + Vite 8 |
| **Backend** | Java 25 + Spring Boot 4.1 |
| **Persistence** | PostgreSQL 18 + Flyway |
| **Storage** | Local streaming storage / SeaweedFS S3 |
| **Deployment** | Docker Compose |
| **Ports** | Frontend: 80, Backend: 8080 |

---

## Directory Structure

```
pdf-tools/
├── backend/                    # Java Spring Boot backend
│   ├── src/main/java/com/pdftools/
│   │   ├── PdfToolsApplication.java    # Main application entry
│   │   ├── config/                      # Spring configuration
│   │   ├── jobs/                        # /api/v1/jobs lifecycle
│   │   ├── operations/                  # Modular PDF operation contract
│   │   ├── storage/                     # Local and S3 streaming adapters
│   │   ├── controller/
│   │   │   └── PdfController.java       # Legacy REST API endpoints
│   │   ├── dto/                         # Data transfer objects
│   │   ├── exception/                   # Custom exceptions
│   │   └── service/
│   │       └── PdfService.java          # PDF processing logic
│   ├── Dockerfile
│   └── pom.xml                          # Maven dependencies
│
├── frontend/                   # React frontend
│   ├── src/
│   │   ├── App.jsx                      # Main app component
│   │   ├── App.css                      # Global styles
│   │   ├── main.jsx                     # Entry point
│   │   ├── components/                  # Reusable UI components
│   │   │   ├── Button.jsx
│   │   │   ├── FileUpload.jsx
│   │   │   ├── Input.jsx
│   │   │   ├── OperationCard.jsx
│   │   │   ├── PdfViewer.jsx            # PDF preview component
│   │   │   └── Toast.jsx
│   │   ├── pages/                       # Page components
│   │   │   ├── MergePage.jsx            # Merge PDFs
│   │   │   ├── SplitPage.jsx            # Split PDF
│   │   │   ├── ExtractPage.jsx          # Extract pages
│   │   │   ├── RemovePage.jsx           # Remove pages
│   │   │   ├── AddTextPage.jsx          # Add text overlay
│   │   │   ├── SignaturePage.jsx        # Add signatures
│   │   │   ├── WatermarkPage.jsx        # Add watermarks
│   │   │   ├── RedactPage.jsx           # Redact content
│   │   │   ├── PdfToWordPage.jsx        # PDF to Word
│   │   │   ├── PdfToMarkdownPage.jsx    # Structured Markdown bundle
│   │   │   ├── CompressPage.jsx         # PDF size reduction
│   │   │   ├── RepairPage.jsx           # qpdf recovery reports
│   │   │   └── OperationPage.css        # Shared operation styles
│   │   └── services/
│   │       └── pdfService.js            # API client
│   ├── Dockerfile
│   ├── nginx.conf                       # Nginx reverse proxy config
│   └── package.json
│
├── docker-compose.yml          # Container orchestration
├── README.md                   # Project documentation
└── AGENTS.md                   # This file
```

---

## Tech Stack Details

### Frontend
| Library | Version | Purpose |
|---------|---------|---------|
| React | 19.2 | UI framework |
| Vite | 8.x | Build tool |
| react-pdf | latest | PDF rendering |
| react-router-dom | 7.x | Routing |
| axios | latest | HTTP client |
| framer-motion | latest | Animations |
| lucide-react | latest | Icons |

### Backend
| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 4.1 | Application framework |
| Apache PDFBox | 3.x | PDF manipulation |
| Apache POI | 5.x | DOCX generation |
| PostgreSQL | 18 | Job metadata |
| Flyway | managed | Schema migrations |

---

## Key Files Reference

### When modifying PDF operations:
- **Operation contract**: `backend/src/main/java/com/pdftools/operations/PdfOperation.java`
- **Merge implementation**: `backend/src/main/java/com/pdftools/operations/merge/`
- **Split implementation**: `backend/src/main/java/com/pdftools/operations/split/`
- **Remove implementation**: `backend/src/main/java/com/pdftools/operations/remove/`
- **Rotate implementation**: `backend/src/main/java/com/pdftools/operations/rotate/`
- **Organize implementation**: `backend/src/main/java/com/pdftools/operations/organize/`
- **Crop implementation**: `backend/src/main/java/com/pdftools/operations/crop/`
- **Page number implementation**: `backend/src/main/java/com/pdftools/operations/pagenumbers/`
- **Protect implementation**: `backend/src/main/java/com/pdftools/operations/protect/`
- **Unlock implementation**: `backend/src/main/java/com/pdftools/operations/unlock/`
- **PDF-to-JPG implementation**: `backend/src/main/java/com/pdftools/operations/pdfjpg/`
- **JPG-to-PDF implementation**: `backend/src/main/java/com/pdftools/operations/jpgpdf/`
- **Watermark implementation**: `backend/src/main/java/com/pdftools/operations/watermark/`
- **Edit implementation**: `backend/src/main/java/com/pdftools/operations/edit/`
- **Secure Redact implementation**: `backend/src/main/java/com/pdftools/operations/redact/`
- **Office conversion sandbox**: `backend/src/main/java/com/pdftools/operations/office/`
- **Word-to-PDF implementation**: `backend/src/main/java/com/pdftools/operations/wordpdf/`
- **PowerPoint-to-PDF implementation**: `backend/src/main/java/com/pdftools/operations/pptpdf/`
- **Excel-to-PDF implementation**: `backend/src/main/java/com/pdftools/operations/excelpdf/`
- **HTML-to-PDF implementation**: `backend/src/main/java/com/pdftools/operations/htmlpdf/`
- **HTML converter sidecar**: `html-converter/`
- **PDF-to-Word implementation**: `backend/src/main/java/com/pdftools/operations/pdfword/`
- **PDF-to-PowerPoint implementation**: `backend/src/main/java/com/pdftools/operations/pdfppt/`
- **PDF-to-Excel implementation**: `backend/src/main/java/com/pdftools/operations/pdfexcel/`
- **Office queue daemon**: `backend/src/main/java/com/pdftools/operations/office/OfficeConverterDaemonMain.java`
- **Job lifecycle**: `backend/src/main/java/com/pdftools/jobs/`
- **Legacy backend logic**: `backend/src/main/java/com/pdftools/service/PdfService.java`
- **Frontend job API**: `frontend/src/services/jobService.js`
- **Legacy frontend API**: `frontend/src/services/pdfService.js`

### When modifying UI/styling:
- **Visual authority**: `DESIGN.md`
- **Product truth**: `PRODUCT.md`
- **Global tokens and typography**: `frontend/src/index.css`
- **Landing surface**: `frontend/src/App.jsx`, `frontend/src/App.css`
- **Shared operation page styles**: `frontend/src/pages/OperationPage.css`
- **Global styles**: `frontend/src/App.css`
- **Page-specific styles**: `frontend/src/pages/[PageName].css`

### When modifying PDF preview:
- **PDF viewer component**: `frontend/src/components/PdfViewer.jsx`
- **react-pdf worker config**: Set in each page that uses `<Document>` from react-pdf

---

## Common Patterns

### Adding PDF.js worker (required for react-pdf)
```javascript
import { Document, Page } from 'react-pdf';
import '../lib/pdfWorker';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
```

### File upload handling
```javascript
const handleFilesChange = useCallback((files) => {
  setFile(files[0] || null);
  // Create object URL for preview
  if (files[0]) {
    setFileUrl(URL.createObjectURL(files[0]));
  }
}, []);

// Cleanup URL on unmount
useEffect(() => {
  return () => {
    if (fileUrl) URL.revokeObjectURL(fileUrl);
  };
}, [fileUrl]);
```

### API service pattern
```javascript
// In pdfService.js
export const pdfService = {
  merge: (files) => {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return api.post('/merge', formData, { responseType: 'blob' });
  },
  // ... other operations
};
```

---

## Development Commands

### Docker (recommended)
```bash
# Start all services
docker compose up --build -d

# Rebuild without cache
docker compose build --no-cache

# View logs
docker compose logs -f

# Stop services
docker compose down
```

### Local development
```bash
# Backend (requires Java 25 & Maven)
cd backend && mvn spring-boot:run

# Frontend (requires Node.js)
cd frontend && npm ci && npm run dev
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/jobs` | Create an asynchronous operation |
| GET | `/api/v1/jobs/{jobId}` | Read job status and outputs |
| GET | `/api/v1/jobs/{jobId}/events` | Stream progress |
| DELETE | `/api/v1/jobs/{jobId}` | Cancel a job |
| GET | `/api/v1/jobs/{jobId}/outputs/{outputId}` | Stream an output |
| POST | `/api/pdf/merge` | Merge multiple PDFs |
| POST | `/api/pdf/split` | Split PDF into pages |
| POST | `/api/pdf/extract` | Extract specific pages |
| POST | `/api/pdf/remove` | Remove specific pages |
| POST | `/api/pdf/watermark` | Add text watermark |
| POST | `/api/pdf/add-text` | Add text overlay |
| POST | `/api/pdf/add-signature` | Add signature image |
| POST | `/api/pdf/redact` | Redact areas |
| POST | `/api/pdf/convert/docx` | Convert to DOCX |
| POST | `/api/pdf/convert/markdown` | Convert to Markdown |
| GET | `/api/pdf/health` | Health check |

---

## CSS Architecture

### Layout hierarchy for operation pages:
```
.operation-page          /* Full viewport, flex column */
├── .operation-header    /* Fixed header with title */
└── .operation-content   /* Flex row (column on tablet) */
    ├── .operation-sidebar   /* 360px, scrollable */
    └── .operation-preview   /* Flex 1, PDF preview area */
```

### Media queries:
- `> 1024px`: Side-by-side layout (sidebar + preview)
- `≤ 1024px`: Column layout (sidebar top, preview bottom)
- `≤ 768px`: Reduced padding, smaller fonts

---

## Known Patterns & Gotchas

1. **PDF.js worker**: Every page using `<Document>` from react-pdf must import pdfjs and set the worker source
2. **Object URLs**: Always cleanup with `URL.revokeObjectURL()` in useEffect cleanup
3. **CSS conflicts**: Check both `App.css` and `OperationPage.css` for conflicting rules
4. **Flexbox layout**: `.operation-content` must have `flex-direction: row` for desktop layout
5. **Scrolling**: Sidebar uses `overflow-y: auto` with `max-height: 100%` for proper scrolling

---

## Testing

### Using Playwright for UI testing:
```bash
# Install in project
npm install playwright --save-dev

# Run test script
node test-script.js
```

### Manual testing:
1. Access http://localhost after `docker compose up`
2. Upload PDF files to test operations
3. Check browser DevTools console for errors

---

## Environment Variables

### Frontend (Vite)
- `VITE_API_URL`: API base URL (default: `/api/pdf` via nginx proxy)

### Backend (Spring)
- `SPRING_PROFILES_ACTIVE`: Active profile (`docker` or `dev`)
- `PDF_UPLOAD_DIR`: Temp file storage path
- `CORS_ALLOWED_ORIGINS`: Allowed CORS origins
- `PDF_OPTIONS_ENCRYPTION_KEY`: Base64 32-byte key for sensitive job options
- `PDF_SECURITY_MAX_OUTPUT_BYTES`: Protect/Unlock output byte limit
- `PDF_ENABLED_OPERATIONS`: Comma-separated submission feature flags
- `PDF_TO_JPG_MAX_PIXELS_PER_PAGE`: Per-page raster allocation limit
- `JPG_TO_PDF_MAX_PIXELS_PER_IMAGE`: JPEG input pixel limit
- `WATERMARK_MAX_IMAGE_PIXELS`: Watermark image pixel limit
- `WATERMARK_MAX_IMAGE_BYTES`: Watermark image byte limit
- `WATERMARK_MAX_IMAGE_DIMENSION`: Watermark image side limit
- `EDIT_MAX_ELEMENTS`: Maximum elements in one unified edit plan
- `EDIT_MAX_IMAGE_BYTES`: Per-edit-image byte limit
- `EDIT_MAX_TOTAL_DECODED_IMAGE_BYTES`: Decoded edit-image budget
- `REDACT_MAX_AREAS`: Maximum secure redaction rectangles
- `REDACT_RENDER_DPI`: Sanitized raster resolution
- `REDACT_MAX_PIXELS_PER_PAGE`: Redaction page pixel limit
- `REDACT_WORKER_HEAP_BYTES`: Isolated redaction heap cap
- `REDACT_WORKER_TIMEOUT`: Isolated redaction wall-time cap
- `OFFICE_LIBREOFFICE_BINARY`: LibreOffice executable
- `OFFICE_CONVERSION_MODE`: `queue` sidecar or explicit local `direct`
- `OFFICE_QUEUE_REQUEST_ROOT`: Backend-write/sidecar-read request queue
- `OFFICE_QUEUE_RESPONSE_ROOT`: Sidecar-write/backend-read response queue
- `OFFICE_QUEUE_SIGNAL_ROOT`: Backend-write/sidecar-read signal queue
- `OFFICE_SIDECAR_WORK_ROOT`: Size-limited sidecar scratch mount
- `OFFICE_WORKER_USER`: Non-root native converter identity
- `OFFICE_MAX_INPUT_BYTES`: Office input byte limit
- `OFFICE_MAX_EXPANDED_INPUT_BYTES`: Expanded OOXML limit
- `OFFICE_MAX_ADDRESS_SPACE_BYTES`: Linux converter address-space cap
- `OFFICE_CPU_TIME_SECONDS`: Converter CPU limit
- `OFFICE_WALL_TIMEOUT`: Converter wall-time limit
