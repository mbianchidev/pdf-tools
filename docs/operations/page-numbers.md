# Add Page Numbers

Operation key: `page-numbers`

Add Page Numbers accepts one PDF and returns one deterministic PDF with numbers
drawn on a selected page range.

## Options

```json
{
  "pages": "2-",
  "start": 1,
  "template": "Page {page} of {total}",
  "font": "helvetica-bold",
  "fontSize": 12,
  "position": "bottom-center",
  "margin": 24,
  "outputFilename": "numbered.pdf"
}
```

- `pages` uses the shared grammar and defaults to `all`.
- `start` is the number assigned to the first selected page, from 0 to 1,000,000.
- `template` is printable ASCII, must contain `{page}`, and may also use `{total}`
  for document page count and `{source}` for the source page number.
- `font` supports Helvetica, Times, and Courier in regular or bold variants.
- `fontSize` is 6-72 points.
- `position` supports top or bottom with left, center, or right alignment.
- `margin` is 0-144 points from the visual page edge.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

Selected pages are numbered in document order even if the expression lists them out
of order. Placement is calculated in the visible crop box and remains horizontal at
0°, 90°, 180°, and 270° page rotations.

## Validation and fidelity

Duplicate, malformed, descending, and out-of-range page expressions are rejected.
Templates with unknown placeholders or unsupported glyphs are rejected. Standard
PDF fonts keep the output self-contained without external font files.

Page numbers are appended after each page is rebuilt through the bounded sanitizer.
Annotations/actions and document-level structures are removed; optional-content,
Type 3, soft-mask, and unsupported transparency cases fail explicitly.
