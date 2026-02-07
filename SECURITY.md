# Security Policy

## Supported Versions

We release patches for security vulnerabilities. Currently supported versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of PDF Tools seriously. If you discover a security vulnerability, please report it to us privately.

### How to Report

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them using GitHub's Security Advisory feature:

- **GitHub Security Advisory**: Use the [Security Advisory](https://github.com/mbianchidev/pdf-tools/security/advisories/new) feature to report vulnerabilities privately

This ensures the vulnerability can be assessed and fixed before being publicly disclosed.

### What to Include

Please include the following information in your report:

- Type of vulnerability
- Full paths of source file(s) related to the vulnerability
- The location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit it

### Response Timeline

- **Acknowledgment**: We will acknowledge receipt of your vulnerability report within 48 hours
- **Initial Assessment**: We will provide an initial assessment within 5 business days
- **Status Updates**: We will keep you informed about our progress
- **Resolution**: We aim to resolve critical vulnerabilities within 30 days
- **Disclosure**: We will coordinate with you on the disclosure timeline

## Security Best Practices

When using PDF Tools in production, we recommend:

### General Security

- Always use the latest stable version
- Keep all dependencies up to date
- Enable HTTPS/TLS for all connections
- Implement proper authentication and authorization
- Use environment variables for sensitive configuration

### File Upload Security

- Validate file types and sizes before processing
- Scan uploaded files for malware
- Implement rate limiting to prevent abuse
- Set appropriate file size limits
- Store temporary files securely and clean them up
- **Use strict filename validation to prevent path traversal attacks** ✓ (Implemented)
- **Restrict file extensions to expected types only** ✓ (Implemented: .pdf, .md, .docx)
- **Ensure uploaded files cannot escape the designated storage directory** ✓ (Implemented)

### API Security

- Use API keys or JWT tokens for authentication
- Implement rate limiting on API endpoints
- Validate and sanitize all input parameters
- Set appropriate CORS policies
- Log security-relevant events

### Infrastructure Security

- Run containers with minimal privileges
- Use read-only file systems where possible
- Regularly update Docker base images
- Implement network segmentation
- Monitor for suspicious activity

## Known Limitations

This is a basic implementation suitable for trusted environments. For production use, consider:

- User authentication and authorization
- ~~Input validation and sanitization~~ ✓ **File path validation implemented**
- Rate limiting and abuse prevention
- Malware scanning for uploaded files
- Secure temporary file handling with automatic cleanup
- Content Security Policy (CSP) headers
- Database security if adding persistence

## Security Updates

### Recent Fixes

#### Path Traversal Vulnerability (Fixed in v1.0.1)
**Date**: February 2026  
**Severity**: Critical  
**Component**: File download functionality

**Description**: A path traversal vulnerability was identified in the `downloadFile` method where user-supplied filenames were used directly in file path operations without proper validation. This could have allowed an attacker to read arbitrary files from the system.

**Impact**: An unauthenticated attacker could potentially:
- Read sensitive files outside the upload directory (e.g., `/etc/passwd`, system configuration files)
- Access source code or configuration files
- Potentially escalate privileges by reading sensitive data

**Mitigation**: The vulnerability has been fixed by implementing comprehensive filename validation:
- Added strict regex validation for filenames (alphanumeric, dots, hyphens, underscores only)
- Blocked path traversal attempts (`..`, `/`, `\`)
- Prevented null byte injection attacks
- Restricted file extensions to `.pdf`, `.md`, and `.docx` only
- Added real path validation to ensure files are within the upload directory
- Improved error handling with specific messages for different failure cases

**Recommendation**: All users should upgrade to v1.0.1 or later immediately.

---

Security updates will be released as soon as possible. We will:

1. Publish a security advisory in GitHub
2. Release a patch version
3. Update this document with details (after responsible disclosure)
4. Notify users through GitHub releases

## Bug Bounty Program

We currently do not have a bug bounty program. However, we deeply appreciate security researchers who report vulnerabilities responsibly.

## Contact

For security-related questions or concerns that are not vulnerabilities, please open a regular GitHub issue or start a discussion.

## Acknowledgments

We would like to thank all security researchers who have responsibly disclosed vulnerabilities to us.
