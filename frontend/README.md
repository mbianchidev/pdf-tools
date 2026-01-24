# PDF Tools - Frontend

A modern, production-ready React application for PDF manipulation. Features a clean, distinctive UI with drag-and-drop file upload and comprehensive PDF operations.

## Features

### PDF Operations
- **Merge PDFs**: Combine multiple PDF files into one document
- **Split PDF**: Split a PDF into individual pages
- **Extract Pages**: Extract specific pages (e.g., "1,3,5-7")
- **Remove Pages**: Remove specific pages from a PDF
- **Add Watermark**: Add text watermarks to all pages
- **Add Text**: Add custom text at specific positions
- **Add Signature**: Add signature images to PDFs
- **Redact Content**: Redact sensitive information
- **Convert to Markdown**: Convert PDF to Markdown format
- **Convert to DOCX**: Convert PDF to Microsoft Word format

### UI Features
- Drag-and-drop file upload
- Real-time file preview
- Success/error notifications
- Responsive design (mobile, tablet, desktop)
- Smooth animations and transitions
- Loading states
- Form validation

## Tech Stack

- **React 18** - UI framework
- **Vite** - Build tool and dev server
- **Framer Motion** - Animation library
- **React Dropzone** - File upload with drag & drop
- **Axios** - HTTP client for API calls
- **Lucide React** - Icon library
- **CSS3** - Custom styling with CSS variables

## Design System

### Color Palette
- **Primary**: Deep Indigo (#4F46E5)
- **Secondary**: Vibrant Coral (#F43F5E)
- **Accent**: Warm Amber (#F59E0B)
- **Success**: Emerald (#10B981)
- **Error**: Red (#EF4444)

### Typography
- **Headings**: Outfit (Google Fonts)
- **Body**: DM Sans (Google Fonts)

### Key Design Principles
- Refined modern aesthetic
- Distinctive color combinations
- Smooth, purposeful animations
- Clear visual hierarchy
- Accessible contrast ratios

## Getting Started

### Prerequisites
- Node.js 16+ and npm
- Backend API running on `http://localhost:8080`

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Development Server
The app will be available at `http://localhost:5173`

## Project Structure

```
frontend/
├── src/
│   ├── components/         # Reusable UI components
│   │   ├── Button.jsx
│   │   ├── FileUpload.jsx
│   │   ├── Input.jsx
│   │   ├── OperationCard.jsx
│   │   └── Toast.jsx
│   ├── services/          # API service layer
│   │   └── pdfService.js
│   ├── App.jsx            # Main application component
│   ├── App.css            # Application styles
│   ├── index.css          # Global styles & design system
│   └── main.jsx           # Application entry point
├── public/                # Static assets
├── index.html             # HTML template
├── package.json           # Dependencies
└── vite.config.js         # Vite configuration
```

## API Integration

The frontend connects to the backend API at `http://localhost:8080/api/pdf`.

### API Endpoints
- `POST /merge` - Merge multiple PDFs
- `POST /split` - Split PDF into pages
- `POST /extract` - Extract specific pages
- `POST /remove` - Remove specific pages
- `POST /watermark` - Add watermark
- `POST /add-text` - Add text to PDF
- `POST /add-signature` - Add signature image
- `POST /redact` - Redact content
- `POST /convert/markdown` - Convert to Markdown
- `POST /convert/docx` - Convert to DOCX

All endpoints accept `multipart/form-data` and return file blobs.

## Environment Configuration

To change the API base URL, edit `src/services/pdfService.js`:

```javascript
const API_BASE_URL = 'http://localhost:8080/api/pdf';
```

For production, consider using environment variables:

```javascript
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/pdf';
```

## Component Usage

### Button Component
```jsx
<Button 
  variant="primary"    // primary, secondary, outline, ghost, danger
  size="md"           // sm, md, lg
  loading={false}
  disabled={false}
  icon={<Icon />}
  onClick={handleClick}
>
  Click me
</Button>
```

### FileUpload Component
```jsx
<FileUpload
  onFilesChange={setFiles}
  files={files}
  accept={{ 'application/pdf': ['.pdf'] }}
  multiple={true}
  maxFiles={10}
/>
```

### Input Component
```jsx
<Input
  label="Label"
  type="text"
  placeholder="Enter text"
  value={value}
  onChange={handleChange}
  error="Error message"
  helperText="Helper text"
  required={false}
  icon={<Icon />}
/>
```

### Toast Notifications
```jsx
addToast('Success message', 'success');
addToast('Error message', 'error');
addToast('Warning message', 'warning');
```

## Customization

### Colors
Edit color variables in `src/index.css`:
```css
:root {
  --color-primary-500: #4F46E5;
  --color-secondary-500: #F43F5E;
  /* ... */
}
```

### Typography
Change fonts in `index.html` and `src/index.css`:
```css
:root {
  --font-heading: 'Outfit', sans-serif;
  --font-body: 'DM Sans', sans-serif;
}
```

### Animations
Adjust animation settings:
```css
:root {
  --ease-default: cubic-bezier(0.4, 0, 0.2, 1);
  --duration-fast: 150ms;
  --duration-normal: 250ms;
}
```

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Modern mobile browsers

## Performance

- Code splitting with React lazy loading
- Optimized bundle size
- CSS animations (hardware-accelerated)
- Efficient re-rendering with React hooks

## Accessibility

- WCAG AA compliant color contrast
- Keyboard navigation support
- ARIA labels on interactive elements
- Screen reader friendly
- Focus indicators

## License

This project is part of the PDF Tools suite.
