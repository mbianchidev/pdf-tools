# PDF Tools

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue.svg)](https://react.dev/)

A self-hosted PDF workbench for organizing, editing, securing, converting, repairing,
and comparing documents.

<img width="1325" height="804" alt="image" src="https://github.com/user-attachments/assets/452afb5a-57c0-403b-bbbb-86c211f48ddb" />

PDF Tools keeps processing on infrastructure you control. Its React workspace submits
cancellable jobs to a Spring Boot service, streams progress, and returns expiring
artifacts.

## Highlights

- Organize pages with merge, split, extract, remove, rotate, crop, and reorder tools.
- Mark up and secure files with editing, watermarks, signatures, redaction, and encryption.
- Convert between PDF, Office, images, HTML, Markdown, and archival PDF/A formats.
- Run bounded native conversion workers without network access.

See the [operation guides](docs/README.md#operation-guides) for the complete tool catalog
and behavior details.

## Quick start

Docker with Compose is required.

```bash
git clone https://github.com/mbianchidev/pdf-tools.git
cd pdf-tools
docker compose up --build
```

Open <http://localhost>. Start at <http://localhost/new> to load a document and choose
a workflow. The backend API is available locally at <http://localhost:8080/api/v1>.

For local development, configuration, clean builds, and validation commands, follow the
[development guide](docs/development.md).

## Documentation

- [Documentation index](docs/README.md)
- [Architecture](docs/architecture.md)
- [Jobs API v1](docs/api-v1.md)
- [Product principles](docs/product.md)
- [Design system](docs/design.md)
- [Backend guide](backend/README.md)
- [Frontend guide](frontend/README.md)
- [HTML converter sidecar](html-converter/README.md)

## Stack

React 19 and Vite 8 power the frontend. Java 25 and Spring Boot 4.1 provide the API,
with PostgreSQL for job metadata and local or S3-compatible streaming artifact storage.

## Project

[Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Support](SUPPORT.md) ·
[Code of Conduct](CODE_OF_CONDUCT.md) · [MIT License](LICENSE)
