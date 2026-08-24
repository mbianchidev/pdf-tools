# PDF to JPG

Operation key: `pdf-to-jpg`

PDF to JPG renders selected pages and returns one deterministic ZIP archive.

## Options

```json
{
  "pages": "1-3,5",
  "dpi": 150,
  "quality": 85,
  "outputFilename": "images.zip"
}
```

- `pages` uses the shared page-expression grammar, defaults to `all`, rejects
  duplicates, and is emitted in source-document order.
- `dpi` is an integer from 72 through 300 and defaults to 150.
- `quality` is an integer from 10 through 100 and defaults to 85.
- `outputFilename` is optional and must end in `.zip` within 120 UTF-8 bytes.

Every selected page becomes `{source}_page_NNNN.jpg`. Even a one-page selection
returns a ZIP so browser download behavior remains predictable.
Resolution is physical DPI: non-default PDF `/UserUnit` values are included when
calculating and rendering output dimensions.

## Rendering and limits

PDFBox renders RGB pages sequentially with source-image subsampling enabled.
Before allocating a page bitmap, the operation validates its output dimensions
against a 16,384-pixel side limit and a 20-million-pixel area limit. The default
deployment also limits each JPG to 50 MiB, all JPGs to 512 MiB, the archive to
520 MiB, the document to 1,000 pages, and the selection to 500 pages.

Rendering uses disk-backed PDFBox scratch storage and disk-backed ImageIO cache.
The renderer runs in a killable non-root Java worker capped at 256 MiB heap and
five minutes by default. Cancellation or timeout terminates the worker, including
while PDFBox is decoding or painting a page. JPEGs and the final ZIP are byte-bounded,
deterministic, cancellation-aware, and cleaned with the job workspace.

Encrypted PDFs are rejected; use Unlock PDF first. JPG output is rasterized and
does not retain searchable text, vectors, links, forms, annotations, layers, or
document metadata. Color output uses the runtime's PDFBox/ImageIO color pipeline;
exact ICC-managed print parity is not guaranteed.
