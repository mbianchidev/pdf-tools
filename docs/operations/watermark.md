# Watermark PDF

Operation key: `watermark`

Watermark PDF adds styled text or image overlays to selected pages.

## Text options

```json
{
  "mode": "text",
  "pages": "1-3,5",
  "text": "CONFIDENTIAL",
  "font": "helvetica-bold",
  "fontSize": 48,
  "color": "#4f46e5",
  "opacity": 0.3,
  "rotation": 45,
  "x": 0.5,
  "y": 0.5
}
```

Text is printable ASCII within 100 characters. Fonts are Helvetica, Times, or
Courier in regular/bold variants. Font size is 8–144 points.

## Image options

Image mode receives the PDF first and one PNG/JPEG second.

```json
{
  "mode": "image",
  "pages": "odd",
  "imageWidthPercent": 35,
  "opacity": 0.3,
  "rotation": 0,
  "x": 0.5,
  "y": 0.5
}
```

`imageWidthPercent` is 5–100. Image input is capped at 10 MiB, 4,096 pixels per
side, and four million pixels. JPEGs use the shared metadata-stripped, isolated
entropy-validation pipeline and preserve validated DCT data; PNGs are losslessly
embedded after bounded decoding.

## Shared controls and fidelity

- `pages` uses the shared page-expression grammar and defaults to `all`.
- `opacity` is real PDF alpha from 0.05 through 1.
- `rotation` is -180 through 180 degrees clockwise.
- `x` and `y` are normalized visual coordinates from the top-left, each 0–1.
- `outputFilename` is optional `.pdf` within 120 UTF-8 bytes.

Placement accounts for crop-box origins, page rotation, and PDF `/UserUnit`.
Watermarking uses the hardened page-copy pipeline, so annotations/actions and
document-level outlines, labels, and attachments are removed as documented for
page-copy tools.
