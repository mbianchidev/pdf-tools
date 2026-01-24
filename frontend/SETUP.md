# Quick Start Guide

## Prerequisites
- Node.js 16 or higher
- npm (comes with Node.js)
- Backend API running on port 8080

## Installation

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Configure API URL (optional):**
   - Copy `.env.example` to `.env`
   - Update `VITE_API_URL` if your backend is on a different URL

3. **Start development server:**
   ```bash
   npm run dev
   ```
   The app will be available at http://localhost:5173

## Development

### Available Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build production bundle
- `npm run preview` - Preview production build locally
- `npm run lint` - Run ESLint

### Project Structure

```
src/
├── components/          # Reusable UI components
│   ├── Button.*        # Button component with variants
│   ├── FileUpload.*    # Drag-and-drop file upload
│   ├── Input.*         # Form input with validation
│   ├── OperationCard.* # Expandable operation cards
│   └── Toast.*         # Toast notifications
├── services/           # API integration
│   └── pdfService.js   # PDF API client
├── App.*               # Main application
├── index.css           # Global styles & design system
└── main.jsx            # Entry point
```

## Making Changes

### Adding a New Operation

1. Add operation config to `operations` array in `App.jsx`
2. Add case in `handleExecute` switch statement
3. Add operation-specific form fields in `renderOperationContent`

### Customizing Colors

Edit CSS variables in `src/index.css`:
```css
:root {
  --color-primary-500: #4F46E5;
  --color-secondary-500: #F43F5E;
}
```

### Customizing Fonts

Update Google Fonts link in `index.html` and CSS variables in `src/index.css`

## Building for Production

```bash
npm run build
```

Output will be in the `dist/` directory. Deploy these files to your web server.

## Troubleshooting

### API Connection Issues
- Ensure backend is running on http://localhost:8080
- Check browser console for CORS errors
- Verify API endpoints match backend implementation

### Build Errors
- Delete `node_modules` and `package-lock.json`
- Run `npm install` again
- Clear npm cache: `npm cache clean --force`

### Port Already in Use
- Kill process on port 5173
- Or change port in `vite.config.js`:
  ```js
  export default defineConfig({
    server: { port: 3000 }
  })
  ```

## Production Deployment

### Using Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;
    root /path/to/dist;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

### Using Apache

```apache
<VirtualHost *:80>
    ServerName your-domain.com
    DocumentRoot /path/to/dist
    
    <Directory /path/to/dist>
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted
        
        RewriteEngine On
        RewriteBase /
        RewriteRule ^index\.html$ - [L]
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>
    
    ProxyPass /api http://localhost:8080/api
    ProxyPassReverse /api http://localhost:8080/api
</VirtualHost>
```

## Support

For issues or questions, refer to the main README.md or check the backend API documentation.
