# PDF to PowerPoint

Operation key: `pdf-to-powerpoint`

PDF to PowerPoint creates one `.pptx` with exactly one slide per source page.
It provides explicit editable and visual modes.

## Options

```json
{
  "mode": "editable",
  "slideSize": "source",
  "includeImages": true,
  "detectTables": true,
  "outputFilename": "presentation.pptx"
}
```

- `mode` is `editable` (default) or `visual`.
- `slideSize` is `source` (the first PDF page), `widescreen` (16:9), or
  `standard` (4:3).
- `includeImages` adds bounded raster images in editable mode.
- `detectTables` converts repeated aligned text columns into editable slide
  tables in editable mode.
- `outputFilename` is optional and must end in `.pptx` within 120 UTF-8 bytes.

## Editable mode

The editable baseline uses shared PDFBox extraction:

- each positioned PDF text line becomes an editable PowerPoint text box;
- common bold font names and physical font sizes become text-run formatting;
- aligned text columns become editable PowerPoint tables;
- drawn raster images become editable picture shapes;
- source-page coordinates, crop boxes, and `/UserUnit` are scaled into the
  selected slide geometry.

PDF drawing instructions do not contain native slide semantics. Vector art,
clipping, equations, forms, annotations, complex columns, grouped shapes,
charts, and uncommon font encodings can require manual correction.
Image-only pages fall back to a slide image when images are enabled.
Pages with PDF page rotation also fall back to a visual slide because editable
PowerPoint shapes cannot safely reproduce every rotated content transform.

## Visual mode

Visual mode renders each page at up to 144 DPI and fits one page image onto one
slide. Oversized pages are downsampled to pixel and dimension budgets while
retaining aspect ratio. Appearance is retained more closely, but slide content
is not editable.

PowerPoint uses one global slide size per presentation. With `source`, the first
PDF page defines that size; later pages are centered and scaled without
distortion. Pages larger than PowerPoint's 56-inch limit scale proportionally.

## Isolation and limits

PDF parsing, extraction, rendering, and PPTX generation run in a killable Java
subprocess with a 512 MiB heap and five-minute wall timeout. Defaults:

- 50 MiB input and 200 pages/slides;
- 2,000,000 extracted text characters and 5,000 text boxes;
- 200 images;
- 20,000,000 pixels per image and 200,000,000 aggregate image pixels;
- 25 MiB per encoded image and 256 MiB aggregate encoded images;
- 20,000,000 rendered pixels per page;
- 16,384 pixels per image side;
- 12 detected table columns;
- 128 MiB PPTX output;
- bounded page-tree depth, nodes, and content streams.

The parent validates the returned PPTX as a bounded ZIP and requires exactly
one slide XML part per source page.

## Fidelity and commercial SDK status

The bundled implementation is an Apache PDFBox/Apache POI best-effort baseline.
No Microsoft PowerPoint or commercial conversion engine is bundled, and no
commercial-SDK parity is claimed. Commercial benchmark runs remain pending
because no evaluation license is configured; all candidates must use the
[shared PDF-to-Office benchmark contract](../benchmarks/pdf-to-office.md).
