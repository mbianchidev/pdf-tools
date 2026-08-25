# PDF Tools - Frontend

A modern, production-ready React application for PDF manipulation with a clean, distinctive UI.

## Features

### PDF Operations
- **Merge PDFs** - Ordered asynchronous merge with validation, progress, and cancellation
- **Split PDF** - Individual pages, page ranges, or fixed groups in one ZIP
- **Extract Pages** - Extract specific pages (e.g., "1,3,5-7")
- **Remove Pages** - Validated ranges, visual selection, progress, and cancellation
- **Rotate PDF** - Whole-document actions and independent page controls
- **Organize PDF** - Visual reorder, rotate, duplicate, and delete controls
- **Crop PDF** - Shared or page-specific percentage margins with exact overlay
- **Add Page Numbers** - Range, template, font, and position preview controls
- **Protect PDF** - Separate passwords and explicit permission controls
- **Unlock PDF** - Remove password encryption with a known credential
- **PDF to JPG** - Select page ranges, DPI, and JPEG quality
- **JPG to PDF** - Reorder images and configure PDF paper layout
- **Watermark PDF** - Text/image modes, styling, opacity, and page selection
- **Edit PDF** - Place text, images, shapes, highlights, and notes
- **Add Text** - Add custom text with fonts, colors, and drag positioning
- **Add Signature** - Type, draw, or upload signatures
- **Redact PDF** - Draw irreversible areas with explicit rasterization warnings
- **Word to PDF** - Upload DOCX/DOC files for isolated LibreOffice conversion
- **PowerPoint to PDF** - Upload PPTX/PPT files for isolated slide conversion
- **Excel to PDF** - Configure workbook print areas and page orientation
- **HTML to PDF** - Render self-contained HTML with isolated Chromium
- **PDF to Word** - Choose editable extraction or visual page preservation
- **PDF to PowerPoint** - Choose editable slide elements or visual page slides
- **PDF to Excel** - Build page worksheets or one sheet per detected table
- **PDF to Markdown** - Recover structure and linked images in a portable ZIP
- **Compress PDF** - Compare lossless, balanced, and extreme size reduction
- **Repair PDF** - Review explicit qpdf recovery reports and warnings

### UI Features
- Documentation-first self-hosting landing page
- Shared visual identity with `mbianchidev/img-tools`
- Drag-and-drop file upload
- Real-time PDF preview
- Success/error toast notifications
- Responsive design (mobile, tablet, desktop)
- Smooth animations with Framer Motion

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| React | 19.2 | UI framework |
| Vite | 8.x | Build tool |
| react-pdf | latest | PDF rendering |
| react-router-dom | 7.x | Routing |
| axios | latest | HTTP client |
| framer-motion | latest | Animations |
| lucide-react | latest | Icons |

## Getting Started

### Prerequisites
- Node.js 22+
- npm 10+

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

The app will be available at http://localhost:5173

### Available Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start development server with hot reload |
| `npm run build` | Build production bundle |
| `npm run preview` | Preview production build locally |
| `npm run lint` | Run ESLint |
| `npm test` | Run Vitest component and unit tests |
| `npm run test:e2e` | Build and run Playwright tests |

## Project Structure

```
frontend/
├── src/
│   ├── components/           # Reusable UI components
│   │   ├── Brand.jsx         # Shared pdf-tools identity
│   ├── features/
│   │   ├── editor/           # Page expressions, coordinates, thumbnails, editor
│   │   └── jobs/             # Job state, SSE progress, and cancellation
│   │   ├── Button.jsx        # Button with variants & loading states
│   │   ├── FileUpload.jsx    # Drag-drop upload with previews
│   │   ├── Input.jsx         # Form input with validation
│   │   ├── OperationCard.jsx # Home page operation cards
│   │   ├── PdfViewer.jsx     # PDF preview component
│   │   └── Toast.jsx         # Toast notification system
│   ├── pages/                # Page components
│   │   ├── MergePage.jsx
│   │   ├── SplitPage.jsx
│   │   ├── ExtractPage.jsx
│   │   ├── RemovePage.jsx
│   │   ├── AddTextPage.jsx
│   │   ├── SignaturePage.jsx
│   │   ├── WatermarkPage.jsx
│   │   ├── RedactPage.jsx
│   │   ├── PdfToWordPage.jsx
│   │   ├── PdfToMarkdownPage.jsx
│   │   ├── CompressPage.jsx
│   │   ├── RepairPage.jsx
│   │   └── OperationPage.css  # Shared operation styles
│   ├── services/
│   │   ├── jobService.js     # Versioned asynchronous jobs client
│   │   └── pdfService.js     # Legacy API client during migration
│   ├── App.jsx               # Main application
│   ├── App.css               # Global styles
│   └── main.jsx              # Entry point
├── public/                   # Static assets
├── Dockerfile                # Container build
├── nginx.conf                # Production server config
└── vite.config.js            # Vite configuration
```

## Design System

### Color Palette
| Color | Hex | Usage |
|-------|-----|-------|
| Primary | `#4F46E5` | Deep Indigo - main actions, buttons |
| Secondary | `#F43F5E` | Vibrant Coral - highlights, accents |
| Accent | `#F59E0B` | Warm Amber - warnings, decorative |
| Success | `#10B981` | Emerald - confirmations |
| Error | `#EF4444` | Red - errors, danger |

### Typography
- **Headings**: Outfit (Google Fonts)
- **Body**: DM Sans (Google Fonts)

## Components

### Button
```jsx
<Button 
  variant="primary"  // primary, secondary, outline, ghost, danger
  size="md"          // sm, md, lg
  loading={false}
  icon={<Download />}
  fullWidth
>
  Submit
</Button>
```

### FileUpload
```jsx
<FileUpload
  onFilesChange={setFiles}
  files={files}
  multiple={true}
  maxFiles={10}
  accept={{ 'application/pdf': ['.pdf'] }}
/>
```

### Input
```jsx
<Input
  label="Email"
  type="email"
  value={email}
  onChange={handleChange}
  error={emailError}
  required
/>
```

## API Integration

New tools communicate through `/api/v1/jobs`; legacy pages continue to use
`/api/pdf/*` while they are migrated. Nginx disables buffering for SSE progress.

```javascript
// pdfService.js
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/pdf';

export const pdfService = {
  merge: (files) => { /* ... */ },
  split: (file, groups) => { /* ... */ },
  // ... other operations
};
```

## Building for Production

```bash
# Create production build
npm run build

# Output is in dist/ directory
```

The production build can be served by any static file server that proxies `/api`.

## Docker

The frontend includes a Dockerfile that builds the React app and serves it via nginx:

```bash
docker build -t pdf-tools-frontend .
docker run -p 80:80 pdf-tools-frontend
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_URL` | `/api/pdf` | Backend API base URL |

## Troubleshooting

### API Connection Issues
- Ensure backend is running on http://localhost:8080
- Check browser console for CORS errors
- Verify nginx configuration in production

### Build Errors
```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
```

### Port Already in Use
Change port in `vite.config.js`:
```js
export default defineConfig({
  server: { port: 3000 }
})
```
