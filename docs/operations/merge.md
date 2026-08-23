# Merge PDF

Operation key: `merge`

Merge PDF combines inputs in their explicit multipart order. The React page supports
drag-and-drop ordering plus keyboard-accessible move controls. The server sorts and
validates persisted input positions before importing any page, so retries produce the
same page order.

## Request

Create a standard v1 job with 2-20 PDF files:

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -F operation=merge \
  -F 'options={"outputFilename":"combined.pdf"}' \
  -F files=@first.pdf \
  -F files=@second.pdf
```

`outputFilename` is optional, must end in `.pdf`, and is capped at 120 UTF-8 bytes.
The default is based on the first input filename and is byte-truncated before the
`_merged.pdf` suffix is added.

## Validation and limits

- 2-20 inputs
- PDF filename and accepted PDF media type
- `%PDF-` header within the first 1,024 bytes
- encrypted inputs rejected until unlocked
- 100 MB aggregate request/input limit
- 10,000 pages per input
- 20,000 pages in the merged output
- unique contiguous input positions

Errors use structured codes including `INVALID_FILE_COUNT`, `INVALID_FILE_TYPE`,
`INVALID_PDF`, `ENCRYPTED_PDF`, `MERGE_INPUT_TOO_LARGE`,
`PDF_PAGE_LIMIT_EXCEEDED`, and `MERGE_PAGE_LIMIT_EXCEEDED`.

## Resource behavior

Uploads stream into configured object storage. Workers materialize inputs into an
isolated workspace, load one source at a time through PDFBox disk-backed random access,
and use a workspace-local temporary-file stream cache for source parsing and output.
Cancellation is checked between files and pages. Partial outputs and workspaces are
removed on failure, cancellation, or restart recovery.

## Fidelity

The operation materializes inherited resources before importing page dictionaries,
content, boxes, and page-level annotations in order. It does not currently promise preservation of document-level
outlines, page labels, embedded files, or interactive form relationships across source
documents. Those limits are intentional and are not presented as commercial-SDK parity.
