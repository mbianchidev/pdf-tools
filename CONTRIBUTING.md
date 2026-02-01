# Contributing to PDF Tools

Thank you for your interest in contributing to PDF Tools! This document provides guidelines and instructions for contributing.

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Submitting Changes](#submitting-changes)
- [Coding Standards](#coding-standards)
- [Testing](#testing)

## Code of Conduct

This project adheres to the Contributor Covenant [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior through GitHub issues or the contact methods specified in the Code of Conduct.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/pdf-tools.git
   cd pdf-tools
   ```
3. **Add the upstream repository**:
   ```bash
   git remote add upstream https://github.com/mbianchidev/pdf-tools.git
   ```

## Development Setup

### Backend Development

1. **Prerequisites**: Java 17+, Maven 3.6+
2. **Setup**:
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```
3. **Run tests**:
   ```bash
   mvn test
   ```

### Frontend Development

1. **Prerequisites**: Node.js 18+, npm 9+
2. **Setup**:
   ```bash
   cd frontend
   npm install
   cp .env.example .env
   npm run dev
   ```
3. **Run tests** (if available):
   ```bash
   npm test
   ```

## Making Changes

### Before You Start

1. **Create a new branch** for your feature or fix:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

2. **Keep your fork updated**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

### Types of Contributions

#### Bug Fixes
- Search existing issues first
- Create an issue if one doesn't exist
- Reference the issue in your pull request

#### New Features
- Discuss the feature in an issue first
- Ensure it aligns with the project goals
- Include tests for new functionality
- Update documentation

#### Documentation
- Fix typos, clarify wording
- Add examples and use cases
- Update outdated information

#### Performance Improvements
- Provide benchmarks showing the improvement
- Ensure no functionality is broken

## Submitting Changes

### Pull Request Process

1. **Update your branch** with the latest main:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests** to ensure everything works:
   ```bash
   # Backend
   cd backend && mvn test
   
   # Frontend
   cd frontend && npm test
   ```

3. **Commit your changes** with clear messages:
   ```bash
   git add .
   git commit -m "feat: add PDF rotation feature"
   ```

4. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Create a Pull Request** on GitHub:
   - Use a clear title and description
   - Reference any related issues
   - Include screenshots for UI changes
   - List any breaking changes

### Commit Message Format

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples**:
```
feat(api): add PDF rotation endpoint
fix(upload): handle large file uploads correctly
docs(readme): update installation instructions
```

## Coding Standards

### Backend (Java)

1. **Follow Java conventions**:
   - Use camelCase for variables and methods
   - Use PascalCase for classes
   - Add JavaDoc comments for public methods
   
2. **Code style**:
   - Indentation: 4 spaces
   - Line length: 120 characters max
   - Use meaningful variable names

3. **Best practices**:
   - Handle exceptions properly
   - Log important operations
   - Validate input parameters
   - Use dependency injection

### Frontend (React)

1. **Follow React best practices**:
   - Use functional components with hooks
   - Keep components small and focused
   - Use meaningful component names
   
2. **Code style**:
   - Indentation: 2 spaces
   - Use ESLint configuration
   - Follow the existing code structure

3. **Best practices**:
   - Handle loading and error states
   - Validate user input
   - Use proper error boundaries
   - Keep state minimal

### Documentation

- Update README.md for user-facing changes
- Update code comments for complex logic
- Add JSDoc/JavaDoc for public APIs
- Include examples in documentation

## Testing

### Backend Tests

1. **Unit tests**: Test individual components
   ```java
   @Test
   void testPdfMerge() {
       // Test implementation
   }
   ```

2. **Integration tests**: Test API endpoints
   ```java
   @SpringBootTest
   @AutoConfigureMockMvc
   class PdfControllerTest {
       // Test implementation
   }
   ```

3. **Run tests**:
   ```bash
   mvn test
   ```

### Frontend Tests

1. **Component tests**: Test React components
   ```javascript
   describe('FileUpload', () => {
     it('should handle file upload', () => {
       // Test implementation
     });
   });
   ```

2. **Run tests**:
   ```bash
   npm test
   ```

### Test Coverage

- Aim for at least 70% test coverage
- Test critical paths thoroughly
- Include edge cases and error scenarios

## Review Process

1. **Automated checks**: Must pass CI/CD pipeline
2. **Code review**: At least one approval required
3. **Testing**: Manually test your changes
4. **Documentation**: Ensure docs are updated

## Questions or Problems?

- Open an issue for bugs or feature requests
- Start a discussion for questions
- Be patient and respectful

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (MIT License).

## Recognition

Contributors will be recognized in:
- The project's README
- Release notes
- GitHub contributors page

Thank you for contributing to PDF Tools! 🙏
