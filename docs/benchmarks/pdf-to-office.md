# PDF-to-Office benchmark contract

PDF-to-Office fidelity must be measured on synthetic, redistributable fixtures.
No customer or personal document may be added to the benchmark corpus.

## Fixture categories

| Category | Required checks |
| --- | --- |
| Paragraphs and headings | text order, font emphasis, heading structure |
| Multi-column layout | column order, overlap, paragraph grouping |
| Tables | rows, columns, merged cells, borders, numeric values |
| Raster images | count, order, dimensions, crop, orientation |
| Vector content | documented fallback and visual difference |
| Pagination | page count, page breaks, paper geometry |
| Scanned pages | explicit OCR/non-OCR behavior |
| Forms and annotations | explicit support or rejection |

## Metrics

- exact retained text and reading order;
- native editable paragraphs, tables, and images;
- source/output page-break agreement;
- rendered-page structural similarity;
- peak memory, wall time, output size, and failure mode;
- redistribution, server deployment, and per-document licensing constraints.

## Current status

| Engine | Status | Decision |
| --- | --- | --- |
| PDFBox + Apache POI Word/PowerPoint baseline | Implemented and fixture-tested | Ship as best effort with editable and visual modes |
| Commercial high-fidelity SDK | Not run; evaluation license unavailable | Do not claim parity; retain adapter/benchmark requirement before adoption |

Any commercial candidate must run the same corpus in an isolated,
network-disabled worker and must pass dependency, licensing, security, and
redaction review before it can replace or supplement the baseline.
