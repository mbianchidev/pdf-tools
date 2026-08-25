# Redact PDF

Operation key: `redact`

Redact PDF burns selected visual regions to black and rebuilds every page from a
fresh raster image. This is the secure replacement for the legacy overlay endpoint.

## Options

```json
{
  "areas": [
    {
      "page": 1,
      "x": 0.1,
      "y": 0.2,
      "width": 0.4,
      "height": 0.15
    }
  ],
  "outputFilename": "sanitized.pdf"
}
```

Coordinates are normalized visual values from the top-left after crop and rotation
are applied. Areas must remain fully inside their page. Duplicate areas, invalid
pages, empty plans, more than 500 total areas, or more than 100 areas on one page
are rejected.

`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.
Encrypted inputs are rejected; use Unlock PDF first.

## Security model

The isolated worker renders each current page, burns the requested pixels to black,
and creates a new PDF containing only one sanitized JPEG image per page. It does
not copy source text, image objects, content streams, annotations, forms, actions,
outlines, page labels, embedded files, metadata, optional-content layers, or
incremental revisions.

Fixture tests attempt recovery through text extraction, raw byte searches, image
object inspection, attachments, annotations, and an earlier incremental revision.
The output is a full rewrite with one `%%EOF` marker and a deterministic document
identifier.

This design intentionally trades editability for a narrow, auditable security
boundary. OCR can still recognize visible, non-redacted page content; pixels inside
redaction rectangles are black in the generated page image.

## Isolation and limits

PDFBox runs in a killable, non-root Java subprocess with a 256 MiB heap and
five-minute wall-time limit by default. Rendering is sequential and uses disk-backed
PDFBox and ImageIO scratch storage. Before allocation, every page is limited to
16,384 pixels on either side and 20 million pixels total. Individual page images,
aggregate image bytes, output bytes, page-tree nodes, depth, and content-stream
counts are bounded. Cancellation terminates the worker and removes partial output.

The default sanitized output uses 200 DPI RGB rendering and JPEG quality 95 while
preserving each page's cropped visual dimensions, rotation, and `/UserUnit`-scaled
physical size.

## Fidelity and licensing

Searchable text, vectors, links, forms, annotations, layers, accessibility structure,
and digital signatures are removed from the output. JPEG encoding can slightly alter
non-redacted pixels and is not an exact ICC-managed print reproduction.

No commercial redaction SDK is bundled and no commercial-SDK parity is claimed.
A licensed SDK benchmark remains pending because no evaluation license is available;
the rasterized implementation is the documented security baseline.
