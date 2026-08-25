# Compare PDF

Operation key: `compare`

Compare PDF accepts two ordered, unencrypted PDFs:

1. baseline;
2. candidate.

It combines reading-order text edits, page/text layout movement, and rendered
pixel differences in one report.

## Options

```json
{
  "renderDpi": 120,
  "pixelTolerance": 12,
  "layoutTolerancePoints": 2,
  "outputFilename": "baseline-vs-candidate-comparison.zip"
}
```

- `renderDpi` is 72–200. Higher values detect smaller visual changes while
  consuming more pixels.
- `pixelTolerance` is 0–255 and ignores per-channel differences at or below the
  selected value.
- `layoutTolerancePoints` is 0.1–20 physical points.
- `outputFilename` is optional and must end in `.zip` within 160 UTF-8 bytes.

Multipart order is authoritative. Reversing the files reverses added/removed
text semantics.

## Outputs

Successful jobs return two ordered artifacts:

1. a deterministic ZIP containing `comparison-report.json` and a red-highlight
   PNG for each visually changed page;
2. the same JSON report as a separate output for direct UI/API use.

```text
comparison-report.json
visual/page-001-diff.png
visual/page-004-diff.png
```

The report includes:

- document/page counts and `identical` or `different` status;
- line additions/removals in reading order;
- page geometry changes and matched text lines moved beyond tolerance;
- different/total rendered pixels and percentage per page;
- aggregate changed-page counts and maximum visual percentage.

Missing pages compare against a white page and appear as text, layout, and
visual differences.

## Comparison model

### Text

PDFBox glyph positions are grouped into words and lines, normalized for
whitespace, and compared with a bounded longest-common-subsequence matrix.
Added and removed lines retain baseline/candidate line numbers. This is semantic
text extraction, not OCR.

### Layout

For matching text lines, physical left/top/font-size positions are compared in
points after applying `/UserUnit`. Page width, height, and rotation are compared
separately.

### Rendered pages

Each page is rendered in RGB at the selected DPI. Pixels whose largest channel
difference exceeds `pixelTolerance` are counted and painted red in the diff
PNG; unchanged context is faded. Crop boxes, rotation, and `/UserUnit` are
included through PDFBox rendering.

## Limits and isolation

Parsing, extraction, rendering, diffing, report generation, and ZIP writing run
in a killable Java worker with a 512 MiB heap and five-minute wall timeout.
Defaults:

- 50 MiB per input and 100 MiB total;
- 200 pages per document;
- 2,000,000 extracted characters per document;
- 1,000 lines per page, 1,000 characters per line, and 1,000,000 LCS matrix
  cells per page;
- 5,000 reported text changes;
- 10,000,000 pixels per rendered page and 200,000,000 aggregate render/canvas
  pixels;
- 8,192 pixels per image side;
- 25 MiB per diff PNG and 256 MiB aggregate diff images;
- 4 MiB JSON report and 300 MiB ZIP;
- bounded page-tree depth, nodes, and content streams.

Cancellation terminates the worker and partial outputs are deleted.

## Fidelity

Extracted text can omit scanned text, clipped glyphs, unsupported encodings, or
content represented only as vectors. Reading order and line grouping are
heuristic for complex columns. Pixel comparison captures appearance but can be
affected by font/rendering differences, anti-aliasing, color management, and
the chosen DPI/tolerance. A zero visual difference does not prove byte-level or
object-level identity; the tool does not compare metadata, attachments,
signatures, forms, or object graphs directly.
