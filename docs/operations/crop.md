# Crop PDF

Operation key: `crop`

Crop PDF accepts one document and returns one deterministic PDF with shared or
per-page crop boxes. Rectangles use normalized visual coordinates: origin at the
top-left of the displayed page, independent of page size.

## Shared crop

```json
{
  "crop": { "x": 0.1, "y": 0.1, "width": 0.8, "height": 0.8 },
  "pages": "all"
}
```

`pages` is optional and defaults to all pages.

## Per-page crops

```json
{
  "crops": [
    {
      "pages": "1-2",
      "rectangle": { "x": 0, "y": 0, "width": 0.8, "height": 1 }
    },
    {
      "pages": "3",
      "rectangle": { "x": 0.1, "y": 0.2, "width": 0.8, "height": 0.6 }
    }
  ]
}
```

Crop instructions must not overlap. Rectangles must be finite, positive, contained
within the visual page, and at least 0.1% wide and high. The coordinate transformer
maps the visual rectangle into the existing crop box for 0°, 90°, 180°, and 270°
pages.

`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

## Validation and limits

- exactly one non-empty PDF input
- `crop` and `crops` are mutually exclusive
- at most 1,000 per-page crop instructions
- duplicate, overlapping, malformed, descending, and out-of-range pages are rejected
- all hardened page-copy limits apply

Common errors include `CROP_REQUIRED`, `INVALID_CROP_OPTIONS`,
`INVALID_CROP_RECTANGLE`, `INVALID_CROP_INSTRUCTION`, and `OVERLAPPING_CROPS`.

## Fidelity

Crop changes the PDF crop box; it does not erase content outside that box. Page
content/resources and media boxes remain unchanged. The frontend uses the same
normalized rectangle model for its shaded preview. As with dependent page-copy tools,
annotations/actions and document-level structures are removed and unsupported layered,
Type 3, soft-mask, and transparency-group cases fail explicitly.
