# Rotate PDF

Operation key: `rotate`

Rotate PDF accepts one document and returns one deterministic PDF with relative
clockwise rotations applied to all or selected pages.

## Whole-document or shared rotation

```json
{ "rotation": 90 }
```

`pages` may narrow the shared rotation with the common page-expression grammar:

```json
{ "rotation": 270, "pages": "1,3-5" }
```

## Independent page rotations

```json
{
  "rotations": [
    { "pages": "1,3", "rotation": 90 },
    { "pages": "2", "rotation": 180 }
  ]
}
```

Each rotation is relative to the page's existing rotation. Allowed values are 90,
180, and 270 degrees. Instructions must not overlap; duplicates inside one page
expression and pages assigned by more than one instruction are rejected.

`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

## Validation and limits

- exactly one non-empty PDF input
- `rotation/pages` and `rotations` are mutually exclusive
- at most 1,000 independent rotation instructions
- malformed, descending, duplicate, overlapping, and out-of-range expressions fail
- source, decoded-content, page-tree, resource, scratch, and output limits are inherited
  from the hardened page-copy engine

Common errors include `ROTATION_REQUIRED`, `INVALID_ROTATION`,
`INVALID_ROTATION_OPTIONS`, `INVALID_ROTATION_INSTRUCTION`,
`OVERLAPPING_ROTATIONS`, and the shared page-expression errors.

## Fidelity

Rotation changes page metadata after each page is rebuilt through the same bounded
sanitizer used by Split and Remove. Page content and boxes are preserved. Annotations,
actions, outlines, page labels, and attachments are removed; unsupported layered,
Type 3, soft-mask, and transparency-group cases fail explicitly. The feature is
available through v1 jobs; no legacy Rotate endpoint is exposed.
