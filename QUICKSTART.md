# Quick Start Guide

This guide will help you get the PDF Tools application up and running quickly.

## Option 1: Docker Compose (Recommended)

The easiest way to run the entire application with one command.

### Prerequisites
- Docker (20.10+)
- Docker Compose (2.0+)

### Steps

1. **Clone the repository**
```bash
git clone https://github.com/mbianchidev/pdf-tools.git
cd pdf-tools
```

2. **Start the application**
```bash
docker-compose up --build
```

This will:
- Build the backend (Java Spring Boot)
- Build the frontend (React)
- Start both services with networking configured
- Backend available at: http://localhost:8080
- Frontend available at: http://localhost

3. **Access the application**

Open your browser and navigate to: **http://localhost**

4. **Stop the application**
```bash
docker-compose down
```

To also remove volumes:
```bash
docker-compose down -v
```

## Option 2: Development Mode

Run backend and frontend separately for development.

### Backend

1. **Prerequisites**
   - Java 17 or higher
   - Maven 3.6+

2. **Start the backend**
```bash
cd backend
mvn spring-boot:run
```

Backend will be available at: http://localhost:8080

### Frontend

1. **Prerequisites**
   - Node.js 18+ 
   - npm 9+

2. **Install dependencies**
```bash
cd frontend
npm install
```

3. **Create environment file**
```bash
cp .env.example .env
```

4. **Start the development server**
```bash
npm run dev
```

Frontend will be available at: http://localhost:5173

**Note**: In development mode, the frontend is configured to connect to the backend at `http://localhost:8080/api/pdf`

## Using the Application

### Merge PDFs
1. Select "Merge PDFs" operation
2. Upload multiple PDF files
3. Click "Execute"
4. Download the merged PDF

### Split PDF
1. Select "Split PDF" operation
2. Upload a PDF file
3. Click "Execute"
4. Download individual pages (filenames returned in response)

### Extract Pages
1. Select "Extract Pages" operation
2. Upload a PDF file
3. Enter page numbers (e.g., "1,3,5")
4. Click "Execute"
5. Download the PDF with extracted pages

### Remove Pages
1. Select "Remove Pages" operation
2. Upload a PDF file
3. Enter page numbers to remove (e.g., "2,4")
4. Click "Execute"
5. Download the modified PDF

### Add Watermark
1. Select "Add Watermark" operation
2. Upload a PDF file
3. Enter watermark text
4. Click "Execute"
5. Download the watermarked PDF

### Add Text
1. Select "Add Text" operation
2. Upload a PDF file
3. Enter text to add
4. Optionally specify position (x, y) and page number
5. Click "Execute"
6. Download the modified PDF

### Add Signature
1. Select "Add Signature" operation
2. Upload a PDF file
3. Upload a signature image (PNG, JPG)
4. Optionally specify position (x, y) and page number
5. Click "Execute"
6. Download the signed PDF

### Redact Content
1. Select "Redact Content" operation
2. Upload a PDF file
3. Enter coordinates (x, y) and dimensions (width, height)
4. Optionally specify page number
5. Click "Execute"
6. Download the redacted PDF

### Convert to Markdown
1. Select "Convert to Markdown" operation
2. Upload a PDF file
3. Click "Execute"
4. Download the Markdown file

### Convert to DOCX
1. Select "Convert to DOCX" operation
2. Upload a PDF file
3. Click "Execute"
4. Download the DOCX file

## Testing the API Directly

You can also test the API using curl or Postman:

### Health Check
```bash
curl http://localhost:8080/api/pdf/health
```

### Merge PDFs
```bash
curl -X POST http://localhost:8080/api/pdf/merge \
  -F "files=@file1.pdf" \
  -F "files=@file2.pdf"
```

### Add Watermark
```bash
curl -X POST http://localhost:8080/api/pdf/watermark \
  -F "file=@document.pdf" \
  -F "text=CONFIDENTIAL"
```

### Download File
```bash
curl -O http://localhost:8080/api/pdf/download/{filename}
```

## Troubleshooting

### Port Already in Use

If ports 80 or 8080 are already in use, modify `docker-compose.yml`:

```yaml
services:
  backend:
    ports:
      - "8081:8080"  # Change 8081 to any available port
  
  frontend:
    ports:
      - "8000:80"    # Change 8000 to any available port
```

### Cannot Upload Large Files

The default max file size is 100MB. To change it:

1. Edit `backend/src/main/resources/application.properties`:
```properties
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=200MB
```

2. Edit `frontend/nginx.conf`:
```nginx
client_max_body_size 200M;
```

3. Rebuild the Docker images:
```bash
docker-compose up --build
```

### CORS Errors

If you encounter CORS errors in development:

1. Make sure the backend is running on port 8080
2. Check `backend/src/main/resources/application.properties`:
```properties
cors.allowed-origins=http://localhost:3000,http://localhost:5173,http://localhost:80
```

### Backend Not Starting

1. Check Java version: `java -version` (should be 17+)
2. Check if port 8080 is available
3. View logs: `docker-compose logs backend`

### Frontend Not Building

1. Check Node.js version: `node -v` (should be 18+)
2. Clear npm cache: `npm cache clean --force`
3. Delete node_modules and reinstall: 
```bash
rm -rf node_modules package-lock.json
npm install
```

## Next Steps

- Read the full [README.md](README.md) for detailed information
- Check [frontend/START_HERE.md](frontend/START_HERE.md) for frontend documentation
- Explore the API endpoints in [README.md](README.md#api-documentation)
- Customize the application configuration

## Getting Help

- Check existing [Issues](https://github.com/mbianchidev/pdf-tools/issues)
- Read the documentation in the `frontend/` directory
- Open a new issue if you encounter problems

## Security Note

This application is designed for trusted environments. For production use:
- Add authentication and authorization
- Implement rate limiting
- Add virus scanning for uploaded files
- Use HTTPS/TLS
- Review and update security settings

Enjoy using PDF Tools! 🎉
