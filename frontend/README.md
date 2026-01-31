# PDF Tools - Frontend

A modern, production-ready React application for PDF manipulation with a clean, distinctive UI.

## Features

### PDF Operations
- **Merge PDFs** - Combine multiple PDF files into one document
- **Split PDF** - Split a PDF into individual pages or custom groups
- **Extract Pages** - Extract specific pages (e.g., "1,3,5-7")
- **Remove Pages** - Remove specific pages from a PDF
- **Add Watermark** - Add text watermarks with positioning and rotation
- **Add Text** - Add custom text with fonts, colors, and drag positioning
- **Add Signature** - Type, draw, or upload signatures
- **Redact Content** - Draw-to-select redaction areas
- **Convert to Markdown** - Convert PDF to Markdown format
- **Convert to DOCX** - Convert PDF to Microsoft Word format

### UI Features
- Drag-and-drop file upload
- Real-time PDF preview
- Success/error toast notifications
- Responsive design (mobile, tablet, desktop)
- Smooth animations with Framer Motion

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| React | 19.2 | UI framework |
| Vite | 7.x | Build tool |
| react-pdf | latest | PDF rendering |
| react-router-dom | 7.x | Routing |
| axios | latest | HTTP client |
| framer-motion | latest | Animations |
| lucide-react | latest | Icons |

## Getting Started

### Prerequisites
- Node.js 18+
- npm 9+

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

## Project Structure

```
frontend/
├── src/
│   ├── components/           # Reusable UI components
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
│   │   ├── ConvertDocxPage.jsx
│   │   ├── ConvertMarkdownPage.jsx
│   │   └── OperationPage.css  # Shared operation styles
│   ├── services/
│   │   └── pdfService.js     # API client
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

The frontend communicates with the backend via `/api/pdf/*` endpoints. In production, nginx proxies these requests to the backend service.

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

The production build is optimized (~138KB gzipped) and can be served by any static file server.

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
