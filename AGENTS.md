# AGENTS.md - AI Coding Agent Guidance

This document provides comprehensive guidance for AI coding agents working on the pdf-tools codebase.

## Project Overview

**Project Name:** pdf-tools  
**Description:** Manipulate your PDFs in the easiest way  
**License:** MIT License (Copyright 2026 Matteo Bianchi)  
**Repository:** mbianchidev/pdf-tools  

This is an early-stage project focused on providing tools for PDF manipulation.

## Current Project Status

**Stage:** Initial Development  
**Last Updated:** 2026-01-31

The project is currently in its initial stages with minimal implementation. The repository contains:
- Basic repository structure
- LICENSE file (MIT)
- README.md with project description
- This AGENTS.md file for AI agent guidance

## Project Structure

```
pdf-tools/
├── .git/                 # Git version control directory
├── LICENSE               # MIT License file
├── README.md            # Project introduction and overview
└── AGENTS.md            # This file - AI agent guidance
```

### Expected Future Structure

As the project develops, the following structure is anticipated:

```
pdf-tools/
├── src/                 # Source code directory
│   ├── core/           # Core PDF manipulation logic
│   ├── cli/            # Command-line interface
│   ├── api/            # API/library interface
│   └── utils/          # Utility functions
├── tests/              # Test files
│   ├── unit/          # Unit tests
│   └── integration/   # Integration tests
├── docs/              # Documentation
├── examples/          # Example usage
├── .github/           # GitHub workflows and configurations
├── LICENSE            # MIT License
├── README.md          # Project overview
├── AGENTS.md          # This file
└── [build config]     # Build configuration (package.json, setup.py, go.mod, etc.)
```

## Tech Stack

### Current State
- **Version Control:** Git
- **License:** MIT

### Technology Recommendations for Future Development

Since the tech stack has not yet been determined, here are considerations for AI agents when implementing features:

**For Python Implementation:**
- Use modern Python (3.8+)
- Consider libraries: PyPDF2, pdfplumber, reportlab, pikepdf
- Package management: pip/poetry
- Testing: pytest
- Linting: pylint, black, mypy

**For JavaScript/TypeScript Implementation:**
- Use modern Node.js (16+)
- Consider libraries: pdf-lib, pdfkit, pdf-parse
- Package management: npm/yarn
- Testing: jest, mocha
- Build: TypeScript compiler, webpack

**For Go Implementation:**
- Use Go 1.18+
- Consider libraries: unipdf, pdfcpu
- Testing: go test
- Module management: go mod

**For Rust Implementation:**
- Use stable Rust
- Consider libraries: lopdf, printpdf
- Package management: cargo
- Testing: cargo test

## Navigation Guidance

### Finding Files

When the project is populated, use these guidelines:

1. **Source Code:** Look in `src/` directory
2. **Tests:** Look in `tests/` or `test/` directory, or files with `_test` or `.test` suffix
3. **Documentation:** Look in `docs/` directory or root-level `.md` files
4. **Configuration:** Look in root directory for config files (`.json`, `.yml`, `.toml`, etc.)
5. **Build Scripts:** Check `Makefile`, `package.json` (scripts), `setup.py`, or similar
6. **CI/CD:** Look in `.github/workflows/` for GitHub Actions

### File Naming Conventions

When creating new files, follow these conventions:

- **Source files:** Use descriptive names in lowercase with underscores (Python) or camelCase (JS/Go)
- **Test files:** Mirror source file names with `_test` or `.test` suffix
- **Documentation:** Use descriptive names with hyphens (e.g., `getting-started.md`)
- **Configuration:** Follow standard conventions (`.eslintrc.json`, `pyproject.toml`, etc.)

## Coding Conventions

### General Principles

1. **Code Quality:**
   - Write clean, readable, and maintainable code
   - Follow SOLID principles
   - Keep functions small and focused
   - Use meaningful variable and function names

2. **Documentation:**
   - Add docstrings/JSDoc comments for public APIs
   - Include inline comments for complex logic
   - Update README.md when adding major features
   - Keep this AGENTS.md updated as the project evolves

3. **Testing:**
   - Write tests for new features
   - Maintain high test coverage (aim for >80%)
   - Include both unit and integration tests
   - Test edge cases and error conditions

4. **Error Handling:**
   - Handle errors gracefully
   - Provide informative error messages
   - Log errors appropriately
   - Don't expose sensitive information in errors

5. **Security:**
   - Validate all inputs
   - Be careful with file operations (path traversal)
   - Don't execute arbitrary code from PDFs
   - Keep dependencies updated

### Language-Specific Conventions

These will be established once the primary language is chosen. Follow the community standards for the selected language/framework.

## Build, Test, and Run Instructions

### Current State

No build system is currently in place.

### Setting Up Development Environment (Future)

When the project is established, typical steps will include:

```bash
# 1. Clone the repository
git clone https://github.com/mbianchidev/pdf-tools.git
cd pdf-tools

# 2. Install dependencies
# (Command depends on tech stack - npm install, pip install -r requirements.txt, etc.)

# 3. Run tests
# (Command depends on tech stack - npm test, pytest, go test, cargo test, etc.)

# 4. Build the project
# (Command depends on tech stack - npm run build, python setup.py build, go build, cargo build, etc.)

# 5. Run the application
# (Command depends on implementation)
```

### Testing Strategy

When implementing tests:
- Run tests before making changes to establish baseline
- Write tests for new features (TDD recommended)
- Run tests after changes to ensure nothing broke
- Use test fixtures for sample PDF files

## Special Considerations for AI Agents

### When Making Changes

1. **First Implementation:**
   - If you're creating the first implementation, choose a tech stack that aligns with:
     - The project's goals (simplicity, performance, portability)
     - Common PDF library availability
     - Modern best practices
   - Document your choice in README.md
   - Set up proper project structure

2. **Adding Features:**
   - Check existing code patterns and follow them
   - Update documentation when adding features
   - Add tests for new functionality
   - Consider backward compatibility

3. **Dependencies:**
   - Prefer well-maintained libraries with active communities
   - Check for security vulnerabilities before adding dependencies
   - Document why each dependency is needed
   - Keep dependency versions pinned for reproducibility

4. **PDF-Specific Considerations:**
   - PDFs are complex binary formats - use established libraries
   - Test with various PDF versions (1.4, 1.7, 2.0)
   - Handle both simple and complex PDFs (with images, fonts, etc.)
   - Consider memory usage for large PDFs
   - Be aware of PDF security features (passwords, encryption)

5. **Cross-Platform Compatibility:**
   - Ensure code works on Linux, macOS, and Windows
   - Use path separators correctly
   - Test file system operations on different platforms

6. **Performance:**
   - Optimize for large PDF files
   - Consider streaming for very large files
   - Profile performance for critical operations
   - Cache results when appropriate

### Common Tasks

1. **Reading PDF metadata:** Extract title, author, page count, etc.
2. **Merging PDFs:** Combine multiple PDFs into one
3. **Splitting PDFs:** Extract pages or split into multiple files
4. **Rotating pages:** Rotate individual or all pages
5. **Extracting text:** Get text content from PDFs
6. **Extracting images:** Extract embedded images
7. **Adding watermarks:** Add text or image watermarks
8. **Compressing PDFs:** Reduce file size
9. **Converting formats:** PDF to image, image to PDF, etc.

### Git Workflow

- Create feature branches for new work
- Write clear commit messages
- Keep commits focused and atomic
- Test before committing
- Update documentation in the same commit as code changes

### Updating This Document

As the project evolves, AI agents should update this AGENTS.md file to reflect:
- Changes in project structure
- New conventions or patterns
- Build/test procedures
- New dependencies or tools
- Lessons learned during development

## Resources

### PDF Specifications
- PDF Reference (ISO 32000)
- Adobe PDF specifications

### Library Documentation
- (Add links to chosen libraries once selected)

### Community Standards
- (Add links to language/framework style guides once selected)

## Questions or Issues?

If you encounter ambiguity or need clarification:
1. Check existing code for patterns
2. Refer to README.md for project goals
3. Follow industry best practices for the chosen tech stack
4. Document your decisions in commit messages
5. Update this file with clarifications

---

**Last Updated:** 2026-01-31  
**Document Version:** 1.0  
**Maintained by:** AI Coding Agents and Project Contributors
