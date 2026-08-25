# PDF to Excel

Operation key: `pdf-to-excel`

PDF to Excel detects aligned text tables and creates one `.xlsx` workbook from
one unencrypted PDF.

## Options

```json
{
  "sheetMode": "pages",
  "includeNonTableText": true,
  "outputFilename": "workbook.xlsx"
}
```

- `sheetMode` is `pages` (default) or `tables`.
- `pages` creates `Page 1`, `Page 2`, and later worksheets. Detected tables and
  optional non-table lines remain in source reading order.
- `tables` creates `Table 1`, `Table 2`, and later worksheets, one per detected
  table. It returns `NO_PDF_TABLES_FOUND` when no aligned table is present.
- `includeNonTableText` applies only to `pages` mode and defaults to `true`.
- `outputFilename` is optional and must end in `.xlsx` within 120 UTF-8 bytes.

Detected header rows receive bold styling, fill, and borders. Repeated aligned
columns become typed cells. Plain integers and decimals without ambiguous
leading zeroes become numeric Excel values; all other content is stored as
literal strings, never formulas.

Page mode always returns one worksheet per source page. A page without selected
extractable content produces an empty worksheet rather than failing the job.

## Detection model

The bundled detector groups positioned PDF glyphs into words and lines, then
recognizes consecutive rows whose horizontal cell starts align. This works for
many visually aligned tables but PDF files do not carry native spreadsheet
semantics. Merged cells, spanning headers, nested tables, ruled layouts,
multi-line cells, rotated content, unusual reading order, charts, images, and
scanned pages can require manual correction. No OCR is bundled.

## Isolation and limits

PDF parsing, table detection, and Apache POI workbook generation run in a
killable Java subprocess with a 512 MiB heap and five-minute wall timeout.
Defaults:

- 50 MiB input and 200 PDF pages;
- 2,000,000 extracted text characters;
- 200 detected tables and 200 worksheets;
- 100,000 rows per worksheet and 100 columns;
- 1,000,000 generated cells;
- 100 MiB XLSX output;
- bounded page-tree depth, nodes, and content streams.

The parent validates the returned workbook as a bounded ZIP containing content
types, the workbook part, and at least one worksheet.

## Fidelity and commercial SDK status

The bundled implementation is an Apache PDFBox/Apache POI best-effort baseline.
No Microsoft Excel or commercial table-extraction engine is bundled, and no
commercial-SDK parity is claimed. Commercial runs remain pending because no
evaluation license is configured; candidates must use the
[shared PDF-to-Office benchmark contract](../benchmarks/pdf-to-office.md).
