# Jobs API v1

Base path: `/api/v1`

## Create a job

```http
POST /api/v1/jobs
Content-Type: multipart/form-data
```

Multipart fields:

| Field | Required | Description |
| --- | --- | --- |
| `operation` | Yes | Enabled kebab-case key, limited to 64 UTF-8 bytes |
| `options` | No | JSON object; defaults to `{}` and is limited to 64 KiB |
| `files` | Yes | One or more ordered input files |

The API returns `202 Accepted`, a `Location` header, and the job representation.
Inputs preserve multipart order.
The deployment accepts at most 100 MB per multipart request and 100 MB per file.

```json
{
  "id": "1c6bf2ad-2f56-4ff6-bc5a-737ca38fe8b4",
  "operation": "merge",
  "status": "RUNNING",
  "version": 2,
  "progress": 25,
  "message": "Processing",
  "errorCode": null,
  "errorMessage": null,
  "cancellationRequested": false,
  "createdAt": "2026-08-23T09:00:00Z",
  "updatedAt": "2026-08-23T09:00:01Z",
  "expiresAt": "2026-08-23T11:00:00Z",
  "outputs": []
}
```

## Read a job

```http
GET /api/v1/jobs/{jobId}
```

Terminal statuses are `COMPLETED`, `FAILED`, and `CANCELLED`. Completed jobs expose
ordered `outputs[]` entries with a `downloadUrl`, size, media type, checksum, and expiry.
After `expiresAt`, job reads and downloads return `410 Gone` even if background storage
cleanup has not completed.

## Stream progress

```http
GET /api/v1/jobs/{jobId}/events
Accept: text/event-stream
```

The stream sends named `job` events containing the full current representation. It
closes after a terminal state. Clients should fall back to `GET /jobs/{jobId}` if the
connection is interrupted.
Expired jobs are rejected before streaming with `410` and `X-Error-Code: JOB_EXPIRED`.

`version` is monotonic. Clients must ignore snapshots whose version is not newer than
the latest state they already applied.

## Cancel a job

```http
DELETE /api/v1/jobs/{jobId}
```

The API returns `202 Accepted`. Pending work is cancelled immediately. Running
operations observe cancellation at progress and I/O checkpoints.

## Download an output

```http
GET /api/v1/jobs/{jobId}/outputs/{outputId}
```

The response streams the artifact with its stored media type, content length,
UTF-8-safe attachment filename, and `Cache-Control: private, no-store`.

## Errors

All API errors use one shape:

```json
{
  "timestamp": "2026-08-23T09:00:00Z",
  "status": 422,
  "code": "PAGE_OUT_OF_RANGE",
  "message": "Page 8 is outside the valid range 1-4",
  "path": "/api/v1/jobs",
  "details": {
    "token": "8"
  }
}
```

Expected status codes include `400` for malformed requests, `404` for disabled or
missing resources, `409` for invalid lifecycle transitions, `413` for configured
limits, and `422` for operation-specific validation.

## Page expressions

The shared grammar accepts:

- pages: `1,3,8`
- closed ranges: `2-5`
- open ranges: `-4`, `7-`
- keywords: `all`, `odd`, `even`

Operations select one of three duplicate policies: keep, deduplicate, or reject.
Descending ranges, empty tokens, page zero, and out-of-range pages are invalid.
An expression may expand to at most 100,000 page selections.

## Merge PDF

`operation=merge` accepts 2-20 ordered PDF inputs and an optional
`options.outputFilename`. See [Merge PDF](operations/merge.md) for validation, resource,
and fidelity details.

## Split PDF

`operation=split` accepts one PDF and supports `individual`, `ranges`, and `fixed`
modes. It returns one ZIP artifact. See [Split PDF](operations/split.md) for options,
limits, and fidelity details.

## Remove Pages

`operation=remove` accepts one PDF and requires `options.pages` as a shared page
expression. It returns one PDF and rejects duplicate, invalid, or all-page removal.
See [Remove Pages](operations/remove.md) for validation and fidelity details.

## Rotate PDF

`operation=rotate` accepts one PDF with either a shared `rotation` plus optional
`pages`, or non-overlapping `rotations[]` instructions for independent page angles.
It returns one PDF. See [Rotate PDF](operations/rotate.md).

## Organize PDF

`operation=organize` accepts one PDF and an ordered `options.pages[]` plan. Repeated
source pages duplicate, omitted source pages delete, order controls reordering, and
each item carries a relative rotation. See [Organize PDF](operations/organize.md).

## Crop PDF

`operation=crop` accepts one PDF and either one normalized `crop` plus optional
`pages`, or non-overlapping `crops[]` instructions. See [Crop PDF](operations/crop.md).

## Add Page Numbers

`operation=page-numbers` accepts one PDF with page ranges, numbering start, template,
font, size, position, and margin options. See [Add Page Numbers](operations/page-numbers.md).

## Protect PDF

`operation=protect` accepts one unencrypted PDF, separate user/owner passwords, and
print/copy/modify/annotation/form/accessibility/assembly permissions. Sensitive options
are AES-GCM encrypted before persistence. See [Protect PDF](operations/protect.md).

## Unlock PDF

`operation=unlock` accepts one encrypted PDF and its current user or owner password.
The password is AES-GCM encrypted before persistence, and the output is a full
unencrypted rewrite. See [Unlock PDF](operations/unlock.md).

## PDF to JPG

`operation=pdf-to-jpg` accepts one unencrypted PDF, a page expression, DPI, and
JPEG quality. It returns one deterministic ZIP with JPG entries in source-page
order. See [PDF to JPG](operations/pdf-to-jpg.md).

## JPG to PDF

`operation=jpg-to-pdf` accepts ordered JPEG multipart inputs plus page size,
orientation, and margin controls. It returns one PDF preserving multipart order.
See [JPG to PDF](operations/jpg-to-pdf.md).

## Watermark PDF

`operation=watermark` accepts one PDF for text mode or a PDF followed by one PNG/JPEG
for image mode. Page expressions, normalized position, rotation, opacity, and
mode-specific styling are supported. See [Watermark PDF](operations/watermark.md).

## Edit PDF

`operation=edit` accepts one PDF followed by up to 10 PNG/JPEG assets and an
ordered `elements[]` plan. Text, image, rectangle, ellipse, line, highlight, and
note elements can be applied in one job. See [Edit PDF](operations/edit.md).

## Redact PDF

`operation=redact` accepts one unencrypted PDF and normalized visual
`areas[]`. Every page is rasterized into a new PDF after selected regions are
burned to black, preventing recovery through text extraction, object inspection,
attachments, or prior revisions. See [Redact PDF](operations/redact.md).

## Word to PDF

`operation=word-to-pdf` accepts one DOCX or DOC file and returns one PDF.
The Docker deployment passes jobs through a volume-backed queue to a separate
networkless LibreOffice container with no database, storage, or backend-secret
mounts. Native process limits and a private profile apply inside that boundary. See
[Word to PDF](operations/word-to-pdf.md).

## PowerPoint to PDF

`operation=powerpoint-to-pdf` accepts one PPTX or PPT file and returns one PDF
page per slide. It reuses the networkless Office sidecar, one-way queue, private
tmpfs, non-root converter identity, and native resource limits. See
[PowerPoint to PDF](operations/powerpoint-to-pdf.md).

## Excel to PDF

`operation=excel-to-pdf` accepts one XLSX or XLS workbook plus
`printAreaMode`, optional custom `printArea`, and `orientation`. A bounded
workbook-preparation step applies print settings before LibreOffice Calc runs in
the shared isolated Office sidecar. See [Excel to PDF](operations/excel-to-pdf.md).

## HTML to PDF

`operation=html-to-pdf` accepts one self-contained UTF-8 HTML or HTM document
plus paper size, orientation, background, and margin controls. It returns one
PDF from a dedicated networkless Playwright/Chromium sidecar. External URLs and
local-file access are blocked. See [HTML to PDF](operations/html-to-pdf.md).

## PDF to Word

`operation=pdf-to-word` accepts one unencrypted PDF and returns one DOCX.
`editable` mode reconstructs positioned text, headings, aligned tables,
embedded images, and pagination. `visual` mode preserves each page as an image.
See [PDF to Word](operations/pdf-to-word.md).

## PDF to PowerPoint

`operation=pdf-to-powerpoint` accepts one unencrypted PDF and returns one PPTX
with one slide per page. `editable` mode creates positioned text boxes, aligned
tables, and picture shapes; `visual` mode creates page-image slides. See
[PDF to PowerPoint](operations/pdf-to-powerpoint.md).

## PDF to Excel

`operation=pdf-to-excel` accepts one unencrypted PDF and returns one XLSX.
`pages` mode creates one worksheet per PDF page with detected tables and
optional surrounding text. `tables` mode creates one worksheet per detected
table and rejects documents without tables. See
[PDF to Excel](operations/pdf-to-excel.md).

## PDF to Markdown

`operation=pdf-to-markdown` accepts one unencrypted, text-based PDF and returns
one ZIP containing `document.md` plus optional linked PNG images. Heading, list,
table, image, and page-break recovery can be enabled independently. Documents
without extractable text fail explicitly. See
[PDF to Markdown](operations/pdf-to-markdown.md).

## Compress PDF

`operation=compress` accepts one unencrypted PDF and returns one PDF. `low`
performs a lossless structural rewrite, while `recommended` and `extreme`
recompress eligible opaque raster images at progressively stronger settings.
The result falls back to the exact source whenever the candidate is not
smaller. See [Compress PDF](operations/compress.md).

## Repair PDF

`operation=repair` accepts one damaged, unencrypted PDF and returns a repaired
PDF plus a JSON report. qpdf warning exits are successful but explicitly marked
`partially-recovered`; clean rewrites are marked `repaired`. See
[Repair PDF](operations/repair.md).

## PDF to PDF/A

`operation=pdf-to-pdfa` accepts one unencrypted PDF and `pdfa-1b`, `pdfa-2b`,
or `pdfa-3b`. LibreOffice Draw performs the conversion in the isolated Office
sidecar, and an isolated veraPDF worker must confirm the exact profile before
the PDF and JSON validation report are published. See
[PDF to PDF/A](operations/pdf-to-pdfa.md).
