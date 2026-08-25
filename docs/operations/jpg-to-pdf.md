# JPG to PDF

Operation key: `jpg-to-pdf`

JPG to PDF embeds JPEG inputs as one PDF page each. Multipart input order is the
PDF page order.

## Options

```json
{
  "pageSize": "a4",
  "orientation": "auto",
  "margin": 24,
  "outputFilename": "images.pdf"
}
```

- `pageSize` is `fit`, `a4`, `letter`, or `legal`; default is `fit`.
- `orientation` is `auto`, `portrait`, or `landscape`; default is `auto`.
- `margin` is 0 through 144 PDF points on every side; default is 24.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

`auto` selects portrait or landscape per image. `fit` interprets image pixels at
96 DPI, adds margins, and scales pages down to at most 1,440 points per side.
Standard paper modes scale images proportionally inside the selected margins.
EXIF orientations 1 through 8 are applied without recompressing the JPEG.
Adobe-tagged CMYK/YCCK images receive the standard inverted decode mapping;
non-Adobe CMYK component values are preserved as encoded.

## Fidelity and limits

JPEG bytes are copied directly into disk-backed PDFBox image streams, preserving
their encoded quality. The default deployment accepts up to 100 images and 100 MiB
total input, rejects images above 16,384 pixels per side or 50 million pixels,
limits aggregate pixels to 500 million, and bounds the PDF at 128 MiB.

Before embedding, a constant-memory parser strips metadata into scratch storage and
a killable Java worker performs a subsampled entropy decode. The worker is capped at
128 MiB heap and two minutes; progressive coefficient buffers are conservatively
limited to 64 MiB. The output is cancellation-aware and deterministic for identical
ordered inputs and options. JPG does not carry transparency; non-JPEG image formats
are rejected.
