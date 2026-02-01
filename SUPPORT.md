# Support

Thank you for using PDF Tools! This document provides information on how to get help with the project.

## Getting Help

### 📖 Documentation

Before asking for help, please check the following resources:

- **[README.md](README.md)** - Project overview, quick start guide, and API documentation
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Development setup and contribution guidelines
- **[AGENTS.md](AGENTS.md)** - Technical architecture and codebase navigation
- **Frontend Documentation** - See [frontend/README.md](frontend/README.md)
- **Backend Documentation** - See [backend/README.md](backend/README.md)

### 💬 GitHub Discussions

For general questions, ideas, or discussions:

- Browse existing discussions: [GitHub Discussions](https://github.com/mbianchidev/pdf-tools/discussions)
- Start a new discussion if your question hasn't been answered
- Categories:
  - **Q&A**: Ask questions about usage or implementation
  - **Ideas**: Suggest new features or improvements
  - **Show and Tell**: Share what you've built with PDF Tools
  - **General**: Any other discussions

### 🐛 Bug Reports

If you've found a bug:

1. Check if it's already reported in [GitHub Issues](https://github.com/mbianchidev/pdf-tools/issues)
2. If not, [create a new issue](https://github.com/mbianchidev/pdf-tools/issues/new)
3. Use the bug report template
4. Include:
   - Clear description of the bug
   - Steps to reproduce
   - Expected vs actual behavior
   - Your environment (OS, browser, versions)
   - Screenshots or error messages if applicable

### ✨ Feature Requests

Have an idea for a new feature?

1. Check if it's already suggested in [GitHub Issues](https://github.com/mbianchidev/pdf-tools/issues)
2. [Create a new feature request](https://github.com/mbianchidev/pdf-tools/issues/new)
3. Use the feature request template
4. Describe:
   - The problem you're trying to solve
   - Your proposed solution
   - Any alternatives you've considered
   - Why this would be useful to others

### 🔒 Security Issues

For security vulnerabilities:

- **DO NOT** open a public issue
- Follow the [Security Policy](SECURITY.md)
- Report privately through GitHub Security Advisories

## Common Issues

### Installation Problems

**Docker issues:**
```bash
# Rebuild without cache
docker compose build --no-cache

# Check container status
docker compose ps

# View logs
docker compose logs -f
```

**Port conflicts:**
- Frontend default: port 80
- Backend default: port 8080
- Change ports in `docker-compose.yml` if needed

### Usage Problems

**File upload fails:**
- Check maximum file size (default: 100MB)
- Ensure file is a valid PDF
- Check browser console for errors

**CORS errors:**
- Verify `cors.allowed-origins` in backend `application.properties`
- Ensure frontend URL is included

**PDF preview not working:**
- Check browser console for PDF.js errors
- Ensure PDF.js worker is properly configured
- Try a different PDF file

### Development Issues

**Backend won't start:**
- Verify Java 17+ is installed
- Check Maven dependencies: `mvn clean install`
- Review logs for specific errors

**Frontend won't start:**
- Verify Node.js 18+ is installed
- Clear node_modules: `rm -rf node_modules && npm install`
- Check for port conflicts

## Response Times

This is an open source project maintained by volunteers:

- **Bug reports**: We aim to respond within 1 week
- **Feature requests**: We aim to respond within 2 weeks
- **Security issues**: We aim to respond within 48 hours
- **Pull requests**: We aim to review within 1 week

Please be patient and respectful. Faster responses are possible for well-documented issues and PRs.

## Contributing

Want to help improve PDF Tools?

- Read [CONTRIBUTING.md](CONTRIBUTING.md)
- Check [good first issue](https://github.com/mbianchidev/pdf-tools/labels/good%20first%20issue) labels
- Join discussions and help answer questions
- Improve documentation
- Write tests
- Fix bugs or implement features

## Community Guidelines

When seeking support:

- ✅ Be respectful and patient
- ✅ Provide detailed information
- ✅ Search for existing answers first
- ✅ Follow up on your issues
- ✅ Help others when you can

- ❌ Don't demand immediate responses
- ❌ Don't post duplicate issues
- ❌ Don't share sensitive information publicly
- ❌ Don't be rude or dismissive

## Additional Resources

### Related Projects

- [Apache PDFBox](https://pdfbox.apache.org/) - PDF library used in backend
- [React-PDF](https://github.com/wojtekmaj/react-pdf) - PDF viewer for React
- [iText](https://itextpdf.com/) - Advanced PDF operations

### Learning Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev/)
- [Docker Documentation](https://docs.docker.com/)

## Commercial Support

Currently, we do not offer commercial support. This project is maintained by volunteers on a best-effort basis.

If you need guaranteed response times or custom features, please reach out via GitHub Discussions to discuss possibilities.

## Staying Updated

- ⭐ Star the repository to follow updates
- 👀 Watch releases for new versions
- 📢 Follow GitHub Discussions for announcements
- 📚 Check the changelog in releases

Thank you for using PDF Tools! 🙏
