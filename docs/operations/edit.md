# Edit PDF

Operation key: `edit`

Edit PDF applies one ordered plan containing content overlays and PDF annotations.
The first multipart file is the PDF; following files are PNG/JPEG assets referenced
by zero-based `imageIndex`.

## Element types

```json
{
  "elements": [
    {
      "type": "text",
      "page": 1,
      "x": 0.5,
      "y": 0.2,
      "text": "Reviewed",
      "font": "helvetica-bold",
      "fontSize": 24,
      "color": "#111111",
      "opacity": 1,
      "rotation": 0
    },
    {
      "type": "image",
      "page": 1,
      "x": 0.5,
      "y": 0.5,
      "width": 0.3,
      "imageIndex": 0,
      "opacity": 1,
      "rotation": 0
    },
    {
      "type": "rectangle",
      "page": 2,
      "x": 0.1,
      "y": 0.2,
      "width": 0.4,
      "height": 0.2,
      "strokeColor": "#4f46e5",
      "fillColor": "none",
      "strokeWidth": 2,
      "opacity": 1
    }
  ]
}
```

Supported types are `text`, `image`, `rectangle`, `ellipse`, `line`,
`highlight`, and `note`. Coordinates and dimensions are normalized visual values
from the top-left. Text uses PDF Standard 14 fonts and printable ASCII. Highlights
and notes are real PDF annotations; other elements are appended page content.

Plans are limited to 500 elements and 10 image assets. Images reuse the bounded
PNG/JPEG preparation and isolated JPEG validation pipeline. Placement accounts for
crop-box origins, page rotation, and `/UserUnit`. Image assets are prepared lazily
after page validation, embedded once, released immediately, and capped by a 64 MiB
decoded-image budget.

The hardened page-copy pipeline rebuilds every page before applying the edit plan.
Original annotations/actions and document-level outlines, labels, and attachments
are removed; new highlight/note annotations from the plan are retained.
