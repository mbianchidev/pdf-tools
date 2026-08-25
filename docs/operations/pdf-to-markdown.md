# PDF to Markdown

Operation key: `pdf-to-markdown`

PDF to Markdown extracts one unencrypted, text-based PDF into a deterministic ZIP
bundle containing structured Markdown and optional linked images.

## Options

```json
{
  "detectHeadings": true,
  "detectLists": true,
  "detectTables": true,
  "includeImages": true,
  "preservePageBreaks": true,
  "outputFilename": "document-markdown.zip"
}
```

- `detectHeadings` maps larger, short text lines to level-one or level-two
  headings.
- `detectLists` recognizes common bullet and numbered-list prefixes.
- `detectTables` turns consecutive, consistently aligned text columns into GFM
  tables.
- `includeImages` extracts bounded raster images as PNG files and adds relative
  Markdown links.
- `preservePageBreaks` adds page comments and thematic breaks between source
  pages.
- `outputFilename` is optional and must end in `.zip` within 120 UTF-8 bytes.

All structure controls default to `true`. When a detector is disabled, source
prefixes are escaped so text is not accidentally interpreted as that Markdown
structure. Extracted text is also escaped so PDF content cannot inject raw HTML
into the generated document.

## Bundle

Every job returns one ZIP so image links remain portable:

```text
document.md
images/page-001-image-001.png
images/page-002-image-001.png
```

Entry names and timestamps are deterministic. The parent process validates the
archive entry set, paths, expanded size, Markdown size, and required
`document.md` before publishing it.

## Detection and fidelity

PDF files contain positioned drawing instructions rather than semantic
headings, lists, tables, paragraphs, or reading order. The bundled PDFBox
implementation groups positioned glyphs into words and lines, sorts them in
visual reading order, compares font sizes for headings, recognizes list
prefixes, detects repeated aligned columns, and places extracted raster images
near surrounding text.

This is best-effort extraction. Complex columns, spanning or merged table cells,
rotated text, clipping, vector artwork, unusual font encodings, and ambiguous
reading order can require manual correction. No OCR is bundled. A PDF with no
extractable text fails with `IMAGE_ONLY_PDF_NOT_SUPPORTED`, including when image
export is disabled; run OCR before submitting scanned or image-only documents.
No commercial conversion parity is claimed.

## Isolation and limits

Parsing, extraction, image decoding, and ZIP generation run in a killable Java
subprocess with a 512 MiB heap and five-minute wall timeout. Defaults:

- 50 MiB input and 200 pages;
- 2,000,000 extracted text characters and 4,000,000 Markdown characters;
- 500 tables with at most 12 detected columns;
- 200 images;
- 20,000,000 pixels per image and 200,000,000 aggregate image pixels;
- 25 MiB per encoded image and 256 MiB aggregate encoded images;
- 8,192 pixels per image side;
- 300 MiB ZIP output;
- bounded page-tree depth, nodes, and content streams.

Cancellation terminates the subprocess and partial output is deleted.
