# PDF Tools

A comprehensive PDF manipulation application with a modern React frontend and robust Java Spring Boot backend.

## Documentation

- **[Frontend README](frontend/README.md)** - React application documentation
- **[Backend README](backend/README.md)** - Spring Boot API documentation
- **[AGENTS.md](AGENTS.md)** - Agent navigation guide for AI assistants

## Features

### PDF Operations
- **Merge PDFs** - Combine multiple PDF files into one
- **Split PDF** - Split a PDF into individual pages
- **Extract Pages** - Extract specific pages from a PDF
- **Remove Pages** - Remove specific pages from a PDF
- **Add Watermark** - Add text watermarks to PDF pages
- **Add Text** - Add custom text to PDFs at specific positions
- **Add Signature** - Add signature images to PDFs
- **Redact Content** - Redact sensitive information with black boxes
- **Convert to Markdown** - Export PDF content as Markdown
- **Convert to DOCX** - Export PDF content as Word documents

### Technology Stack
- **Backend**: Java 17, Spring Boot 3.2.1, Apache PDFBox, iText, Apache POI
- **Frontend**: React 19, Vite 7, Framer Motion, Axios
- **Deployment**: Docker, Docker Compose, Nginx

## Quick Start

### Prerequisites
- Docker and Docker Compose installed
- At least 2GB of available RAM
- Ports 80 and 8080 available

### Run with Docker Compose

```bash
# Clone the repository
git clone https://github.com/mbianchidev/pdf-tools.git
cd pdf-tools

# Build and start all services
docker-compose up --build

# Access the application
# Frontend: http://localhost
# Backend API: http://localhost:8080/api/pdf
```

The application will be available at `http://localhost`. The frontend automatically proxies API requests to the backend.

### Development Mode

#### Backend
```bash
cd backend
mvn spring-boot:run
```

Backend will run on `http://localhost:8080`

#### Frontend
```bash
cd frontend
npm install
npm run dev
```

Frontend will run on `http://localhost:5173`

## API Documentation

### Endpoints

#### Merge PDFs
```
POST /api/pdf/merge
Content-Type: multipart/form-data
Parameters: files[] (multiple PDF files)
```

#### Split PDF
```
POST /api/pdf/split
Content-Type: multipart/form-data
Parameters: file (PDF file)
```

#### Extract Pages
```
POST /api/pdf/extract
Content-Type: multipart/form-data
Parameters: 
  - file (PDF file)
  - pages (comma-separated page numbers, e.g., "1,3,5")
```

#### Remove Pages
```
POST /api/pdf/remove
Content-Type: multipart/form-data
Parameters:
  - file (PDF file)
  - pages (comma-separated page numbers to remove)
```

#### Add Watermark
```
POST /api/pdf/watermark
Content-Type: multipart/form-data
Parameters:
  - file (PDF file)
  - text (watermark text)
```

#### Add Text
```
POST /api/pdf/add-text
Content-Type: multipart/form-data
Parameters:
  - file (PDF file)
  - text (text to add)
  - x (x-coordinate, default: 50)
  - y (y-coordinate, default: 750)
  - page (page number, default: 1)
```

#### Add Signature
```
POST /api/pdf/add-signature
Content-Type: multipart/form-data
Parameters:
  - file (PDF file)
  - signature (signature image file)
  - x (x-coordinate, default: 400)
  - y (y-coordinate, default: 100)
  - page (page number, default: 1)
```

#### Redact Content
```
POST /api/pdf/redact
Content-Type: multipart/form-data
Parameters:
  - file (PDF file)
  - x (x-coordinate)
  - y (y-coordinate)
  - width (width of redaction box)
  - height (height of redaction box)
  - page (page number, default: 1)
```

#### Convert to Markdown
```
POST /api/pdf/convert/markdown
Content-Type: multipart/form-data
Parameters: file (PDF file)
```

#### Convert to DOCX
```
POST /api/pdf/convert/docx
Content-Type: multipart/form-data
Parameters: file (PDF file)
```

#### Download File
```
GET /api/pdf/download/{filename}
```

#### Health Check
```
GET /api/pdf/health
```

## Project Structure

```
pdf-tools/
├── backend/                    # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/pdftools/
│   │   │   │   ├── config/         # Configuration classes
│   │   │   │   ├── controller/     # REST controllers
│   │   │   │   ├── dto/            # Data transfer objects
│   │   │   │   ├── exception/      # Exception handlers
│   │   │   │   ├── service/        # Business logic
│   │   │   │   └── PdfToolsApplication.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                   # React frontend
│   ├── src/
│   │   ├── components/         # React components
│   │   ├── services/           # API service layer
│   │   ├── App.jsx            # Main application
│   │   └── index.css          # Styles
│   ├── public/
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   └── vite.config.js
├── docker-compose.yml          # Docker orchestration
├── LICENSE
└── README.md
```

## Configuration

### Backend Configuration
Edit `backend/src/main/resources/application.properties`:

```properties
# Change port
server.port=8080

# Change max file size
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB

# Change upload directory
pdf.upload.dir=/tmp/pdf-uploads

# Configure CORS
cors.allowed-origins=http://localhost:3000,http://localhost:80
```

### Frontend Configuration
Edit `frontend/src/services/pdfService.js` to change the API base URL:

```javascript
const API_BASE_URL = 'http://localhost:8080/api/pdf';
```

## Building for Production

### Backend JAR
```bash
cd backend
mvn clean package
java -jar target/pdf-tools-backend-1.0.0.jar
```

### Frontend Build
```bash
cd frontend
npm run build
# Output in dist/ directory
```

### Docker Images
```bash
# Build backend
docker build -t pdf-tools-backend ./backend

# Build frontend
docker build -t pdf-tools-frontend ./frontend

# Run with Docker Compose
docker-compose up -d
```

## Testing

### Backend Tests
```bash
cd backend
mvn test
```

### Frontend Tests
```bash
cd frontend
npm test
```

## Troubleshooting

### Common Issues

**Port already in use**
```bash
# Change ports in docker-compose.yml
ports:
  - "8081:8080"  # Backend
  - "8000:80"    # Frontend
```

**File upload fails**
- Check max file size in application.properties
- Ensure upload directory exists and is writable
- Verify nginx client_max_body_size in nginx.conf

**CORS errors**
- Update cors.allowed-origins in application.properties
- Ensure frontend URL is included

**Backend not accessible from frontend**
- Check if backend container is running: `docker ps`
- Verify network connectivity: `docker network inspect pdf-tools_pdf-tools-network`
- Check backend health: `curl http://localhost:8080/api/pdf/health`

## Performance Considerations

- Maximum file size: 100MB (configurable)
- Temporary files are stored in `/tmp/pdf-uploads`
- Files are automatically cleaned up (implement cleanup if needed)
- For production, consider adding:
  - File size validation
  - Rate limiting
  - Authentication/Authorization
  - Virus scanning
  - Cloud storage integration

## Security Notes

- This is a basic implementation suitable for trusted environments
- For production use, implement:
  - User authentication
  - File upload validation
  - Input sanitization
  - Rate limiting
  - HTTPS/TLS
  - API keys or JWT tokens
  - File scanning for malware

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Open an issue on GitHub
- Check existing documentation in the `frontend/docs` directory

## Acknowledgments

- Apache PDFBox for PDF manipulation
- iText for advanced PDF features
- Apache POI for DOCX conversion
- React and Vite for modern frontend development
