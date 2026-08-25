# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

People and teams who need practical PDF manipulation in a browser while retaining
control of the server that processes their documents.

## Product Purpose

PDF Tools provides one self-hosted workspace for combining, reorganizing, editing,
converting, securing, repairing, and comparing PDF documents. Success means users can
complete common document workflows without sending files to a third-party SaaS.

## Positioning

Open source and self-hosted: documents are processed on the operator's own PDF Tools
server through explicit, inspectable jobs.

## Operating Context

The public landing page explains how to self-host the stack and links into the deployed
workspace. Users can start at `/new`, load a PDF, and carry it between compatible PDF
tools with the persistent workflow topbar. Tool routes configure an operation, monitor
progress, and download expiring outputs. The React frontend communicates with a Java
service backed by PostgreSQL and streaming object storage.

## Capabilities and Constraints

- PDF operations run as asynchronous, cancellable jobs with structured errors.
- Anonymous inputs and outputs expire two hours after completion.
- The standard multipart request limit is 100 MB.
- Apache PDFBox is the default PDF engine.
- Office and HTML converters require isolated native processes.
- Fidelity limits must be stated rather than presented as commercial-SDK parity.

## Brand Commitments

- Product name: PDF Tools.
- Use the same design language, typography, layout discipline, and UX patterns as
  `mbianchidev/img-tools`.
- Retain PDF Tools' existing purple accent rather than img-tools' accent color.
- Voice is direct, practical, and specific about what each action does.

## Evidence on Hand

The repository contains the working application, operation tests, API contracts, and
architecture documentation. No customer logos, testimonials, usage metrics, or
commercial fidelity benchmarks are available and they must not be fabricated.

## Product Principles

1. Keep document processing observable and cancellable.
2. Preserve user control through self-hosting and explicit retention.
3. Validate before expensive processing begins.
4. State fidelity and security limits plainly.
5. Keep every pushed state deployable.
