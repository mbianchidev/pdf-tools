# PDF Tools - Agent Navigation Guide

This document provides comprehensive project information for AI agents to efficiently navigate and work with this codebase.

## Project Overview

**PDF Tools** is a full-stack web application for PDF manipulation with a React frontend and Java Spring Boot backend.

| Aspect | Details |
|--------|---------|
| **Type** | Full-stack web application |
| **Frontend** | React 19 + Vite 7 |
| **Backend** | Java 17 + Spring Boot 3.2.1 |
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
│   │   ├── controller/
│   │   │   └── PdfController.java       # REST API endpoints
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
│   │   │   ├── ConvertDocxPage.jsx      # Convert to DOCX
│   │   │   ├── ConvertMarkdownPage.jsx  # Convert to Markdown
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
| Vite | 7.x | Build tool |
| react-pdf | latest | PDF rendering |
| react-router-dom | 7.x | Routing |
| axios | latest | HTTP client |
| framer-motion | latest | Animations |
| lucide-react | latest | Icons |

### Backend
| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 3.2.1 | Application framework |
| Apache PDFBox | 3.x | PDF manipulation |
| iText | 8.x | Advanced PDF operations |
| Apache POI | 5.x | DOCX generation |

---

## Key Files Reference

### When modifying PDF operations:
- **Backend logic**: `backend/src/main/java/com/pdftools/service/PdfService.java`
- **API endpoints**: `backend/src/main/java/com/pdftools/controller/PdfController.java`
- **Frontend API calls**: `frontend/src/services/pdfService.js`

### When modifying UI/styling:
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
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;
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
# Backend (requires Java 17 & Maven)
cd backend && mvn spring-boot:run

# Frontend (requires Node.js)
cd frontend && npm install && npm run dev
```

---

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
