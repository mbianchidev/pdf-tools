# PDF to Word

Operation key: `pdf-to-word`

PDF to Word creates one `.docx` from one unencrypted PDF. It provides two
explicit fidelity modes rather than presenting best-effort extraction as a
perfect conversion.

## Options

```json
{
  "mode": "editable",
  "includeImages": true,
  "detectTables": true,
  "preservePageBreaks": true,
  "outputFilename": "document.docx"
}
```

- `mode` is `editable` (default) or `visual`.
- `includeImages` includes bounded embedded raster images in editable mode.
- `detectTables` converts consecutive, consistently aligned text columns into
  real Word tables in editable mode.
- `preservePageBreaks` inserts a Word page break between source pages.
- `outputFilename` is optional and must end in `.docx` within 120 UTF-8 bytes.

## Editable mode

The editable baseline uses PDFBox text positions and graphics state:

- glyphs are grouped into positioned words and lines;
- font size and common bold font names become Word run formatting;
- larger short lines become heading paragraphs;
- horizontal offsets become bounded paragraph indentation;
- repeated aligned columns become native Word tables;
- drawn raster images become bounded PNG image runs near their page position;
- source pages become explicit Word page breaks.

Mixed source page sizes and orientations become separate Word sections. Pages
larger than Word's 22-inch limit are scaled proportionally.

This is heuristic reconstruction. PDF files store drawing instructions, not
paragraph, heading, table, or reading-order semantics. Complex columns,
overlapping text, equations, vector diagrams, forms, annotations, clipping,
transparency, and unusual font encodings can require manual correction.
Scanned text is not OCRed. Image-only pages fall back to a page image when
images are enabled.

## Visual mode

Visual mode renders each page at 144 DPI and inserts one full-page image per
source page. It preserves appearance and pagination more closely but produces
non-editable, non-searchable page contents.

## Isolation and limits

PDF parsing, image decoding, rendering, and DOCX generation run in a killable
Java subprocess with a 512 MiB heap and five-minute wall timeout. Defaults:

- 50 MiB input;
- 200 pages;
- 2,000,000 extracted text characters;
- 200 images;
- 20,000,000 pixels per image and 200,000,000 aggregate image pixels;
- 25 MiB per encoded image and 256 MiB aggregate encoded images;
- 20,000,000 rendered pixels per page;
- 16,384 pixels per image side;
- 12 detected table columns;
- 128 MiB DOCX output;
- bounded page-tree depth, nodes, and content streams.

The parent process validates the PDF before launch and validates the returned
DOCX as a bounded ZIP containing the required content types and Word document
part.

## Fidelity and commercial SDK status

The bundled implementation is an Apache PDFBox/Apache POI best-effort baseline.
No Microsoft Word, Adobe, Apryse, Foxit, or other commercial conversion engine
is bundled, and no commercial-SDK parity is claimed. The fixture and scoring
contract for future licensed comparisons is documented in
[PDF-to-Office benchmarks](../benchmarks/pdf-to-office.md). Commercial runs
remain pending because no evaluation license is configured.
