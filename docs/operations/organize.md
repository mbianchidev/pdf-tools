# Organize PDF

Operation key: `organize`

Organize PDF accepts one PDF and returns one deterministic PDF assembled from an
ordered page plan. The plan can reorder, rotate, duplicate, and omit source pages.

## Options

```json
{
  "pages": [
    { "page": 3, "rotation": 90 },
    { "page": 1, "rotation": 0 },
    { "page": 1, "rotation": 180 }
  ],
  "outputFilename": "organized.pdf"
}
```

`pages` is required and represents the exact output order. `page` is a one-based
source page number. Repeating a source page duplicates it; omitting a source page
deletes it. `rotation` is relative to the source and must be 0, 90, 180, or 270.

The plan must retain at least one page and may contain at most 1,000 output pages.
`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

## Validation and limits

- exactly one non-empty PDF input
- every plan item requires an integer source page and allowed rotation
- source pages outside the document are rejected with `PAGE_OUT_OF_RANGE`
- decoded-content, page-tree, resource, scratch, structural, and output limits are
  inherited from the hardened page-copy engine

Common errors include `ORGANIZE_PAGES_REQUIRED`, `INVALID_ORGANIZE_PAGE`,
`PAGE_OUT_OF_RANGE`, and the shared bounded PDF/resource errors.

## Fidelity

Each occurrence is rebuilt independently, so duplicate pages can have different
rotations. Page boxes and sanitized page content/resources are preserved. Annotations,
actions, outlines, page labels, and attachments are removed; optional-content layers,
Type 3 fonts, used graphics-state soft masks, and non-device transparency-group color
spaces fail explicitly. Organize is available through v1 jobs and has no legacy route.
