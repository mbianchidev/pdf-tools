# Component Documentation

## Button Component

### Props
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| children | node | required | Button content |
| onClick | function | - | Click handler |
| variant | string | 'primary' | Style variant: primary, secondary, outline, ghost, danger |
| size | string | 'md' | Size: sm, md, lg |
| disabled | boolean | false | Disabled state |
| loading | boolean | false | Loading state (shows spinner) |
| type | string | 'button' | HTML button type |
| icon | node | - | Icon element to display |
| fullWidth | boolean | false | Make button full width |
| className | string | '' | Additional CSS classes |

### Examples
```jsx
// Primary button
<Button variant="primary" onClick={handleClick}>
  Submit
</Button>

// Button with icon
<Button icon={<Download />} variant="secondary">
  Download
</Button>

// Loading button
<Button loading={true}>
  Processing...
</Button>
```

---

## FileUpload Component

### Props
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| onFilesChange | function | required | Callback with file array |
| accept | object | { 'application/pdf': ['.pdf'] } | Accepted file types |
| multiple | boolean | false | Allow multiple files |
| files | array | [] | Current files array |
| maxFiles | number | 10 | Maximum number of files |

### Examples
```jsx
// Single file upload
<FileUpload
  onFilesChange={setFile}
  files={file}
  multiple={false}
/>

// Multiple PDF files
<FileUpload
  onFilesChange={setFiles}
  files={files}
  multiple={true}
  maxFiles={5}
/>

// Image files
<FileUpload
  onFilesChange={setImages}
  files={images}
  accept={{ 'image/*': ['.png', '.jpg', '.jpeg'] }}
/>
```

---

## Input Component

### Props
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| label | string | - | Input label |
| type | string | 'text' | HTML input type |
| placeholder | string | - | Placeholder text |
| value | string/number | - | Input value |
| onChange | function | - | Change handler |
| error | string | - | Error message |
| helperText | string | - | Helper text |
| required | boolean | false | Required field indicator |
| disabled | boolean | false | Disabled state |
| icon | node | - | Icon element |
| className | string | '' | Additional CSS classes |

### Examples
```jsx
// Basic input
<Input
  label="Name"
  placeholder="Enter your name"
  value={name}
  onChange={(e) => setName(e.target.value)}
/>

// Input with validation
<Input
  label="Email"
  type="email"
  value={email}
  onChange={handleEmailChange}
  error={emailError}
  required
/>

// Input with icon
<Input
  label="Search"
  icon={<Search size={20} />}
  placeholder="Search..."
  value={query}
  onChange={(e) => setQuery(e.target.value)}
/>

// Number input
<Input
  label="Age"
  type="number"
  min="0"
  max="120"
  value={age}
  onChange={(e) => setAge(e.target.value)}
  helperText="Enter your age"
/>
```

---

## OperationCard Component

### Props
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| icon | node | required | Card icon |
| title | string | required | Card title |
| description | string | required | Card description |
| isActive | boolean | false | Expanded state |
| onClick | function | - | Click handler |
| children | node | - | Card content (shown when active) |

### Examples
```jsx
<OperationCard
  icon={<Combine size={28} />}
  title="Merge PDFs"
  description="Combine multiple PDF files into one"
  isActive={activeCard === 'merge'}
  onClick={() => setActiveCard('merge')}
>
  <FileUpload {...uploadProps} />
  <Button onClick={handleMerge}>Merge</Button>
</OperationCard>
```

---

## Toast Component

### Usage
Toasts are managed through a container component and state:

```jsx
const [toasts, setToasts] = useState([]);

const addToast = (message, type = 'success', duration = 5000) => {
  const id = Date.now();
  setToasts(prev => [...prev, { id, message, type, duration }]);
};

const removeToast = (id) => {
  setToasts(prev => prev.filter(toast => toast.id !== id));
};

// Render toast container
<ToastContainer toasts={toasts} removeToast={removeToast} />

// Trigger toasts
addToast('Success!', 'success');
addToast('Error occurred', 'error');
addToast('Warning message', 'warning');
```

### Toast Types
- **success**: Green, with check icon
- **error**: Red, with X icon
- **warning**: Yellow, with alert icon

---

## API Service

### pdfService Methods

```javascript
import { pdfService, downloadBlob } from './services/pdfService';

// Merge PDFs
const blob = await pdfService.merge(files);
downloadBlob(blob, 'merged.pdf');

// Split PDF
const blob = await pdfService.split(file);
downloadBlob(blob, 'split.zip');

// Extract pages
const blob = await pdfService.extractPages(file, '1,3,5-7');
downloadBlob(blob, 'extracted.pdf');

// Remove pages
const blob = await pdfService.removePages(file, '2,4');
downloadBlob(blob, 'result.pdf');

// Add watermark
const blob = await pdfService.addWatermark(file, 'CONFIDENTIAL');
downloadBlob(blob, 'watermarked.pdf');

// Add text
const blob = await pdfService.addText(file, 'Hello', 100, 100, 1);
downloadBlob(blob, 'text-added.pdf');

// Add signature
const blob = await pdfService.addSignature(file, signatureImage, 400, 600, 1);
downloadBlob(blob, 'signed.pdf');

// Redact content
const blob = await pdfService.redact(file, 100, 100, 200, 50, 1);
downloadBlob(blob, 'redacted.pdf');

// Convert to markdown
const blob = await pdfService.convertToMarkdown(file);
downloadBlob(blob, 'document.md');

// Convert to DOCX
const blob = await pdfService.convertToDocx(file);
downloadBlob(blob, 'document.docx');
```

### Error Handling

```javascript
try {
  const result = await pdfService.merge(files);
  downloadBlob(result, 'merged.pdf');
  addToast('Success!', 'success');
} catch (error) {
  console.error('Error:', error);
  const message = error.response?.data?.message || 'An error occurred';
  addToast(message, 'error');
}
```

---

## Design System

### Colors
Access via CSS variables:
```css
var(--color-primary-500)    /* Deep Indigo */
var(--color-secondary-500)  /* Vibrant Coral */
var(--color-accent-500)     /* Warm Amber */
var(--color-success)        /* Emerald */
var(--color-error)          /* Red */
var(--color-warning)        /* Amber */
```

### Typography
```css
var(--text-xs)    /* 0.75rem */
var(--text-sm)    /* 0.875rem */
var(--text-base)  /* 1rem */
var(--text-lg)    /* 1.125rem */
var(--text-xl)    /* 1.25rem */
var(--text-2xl)   /* 1.5rem */
var(--text-3xl)   /* 1.875rem */
var(--text-4xl)   /* 2.25rem */
```

### Spacing
```css
var(--space-1)   /* 0.25rem */
var(--space-2)   /* 0.5rem */
var(--space-3)   /* 0.75rem */
var(--space-4)   /* 1rem */
var(--space-6)   /* 1.5rem */
var(--space-8)   /* 2rem */
var(--space-12)  /* 3rem */
var(--space-16)  /* 4rem */
```

### Shadows
```css
var(--shadow-sm)    /* Subtle shadow */
var(--shadow-md)    /* Medium shadow */
var(--shadow-lg)    /* Large shadow */
var(--shadow-xl)    /* Extra large */
var(--shadow-primary)   /* Primary color shadow */
var(--shadow-secondary) /* Secondary color shadow */
```

### Border Radius
```css
var(--radius-sm)    /* 0.375rem */
var(--radius-md)    /* 0.5rem */
var(--radius-lg)    /* 0.75rem */
var(--radius-xl)    /* 1rem */
var(--radius-2xl)   /* 1.25rem */
var(--radius-full)  /* 9999px (circle) */
```

---

## Best Practices

### State Management
- Use `useState` for local component state
- Lift state up when shared between components
- Consider Context API for deeply nested props

### Error Handling
- Always wrap API calls in try-catch
- Show user-friendly error messages
- Log errors to console for debugging

### Performance
- Use React.memo for expensive components
- Avoid inline function definitions in render
- Debounce search inputs and expensive operations

### Accessibility
- Use semantic HTML elements
- Add ARIA labels to icons and buttons
- Ensure keyboard navigation works
- Maintain sufficient color contrast

### Code Style
- Use functional components with hooks
- Keep components small and focused
- Extract reusable logic into custom hooks
- Use descriptive variable and function names
