# Compress PDF

Operation key: `compress`

Compress PDF accepts one unencrypted PDF and returns one PDF whose size is never
larger than the input.

## Options

```json
{
  "mode": "recommended",
  "outputFilename": "document-compressed.pdf"
}
```

- `mode` is `low`, `recommended` (default), or `extreme`.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

## Modes

| Mode | Behavior | Fidelity |
| --- | --- | --- |
| `low` | Lossless PDFBox rewrite with compressed object streams | Keeps existing image data, text, vectors, page geometry, and resources |
| `recommended` | Recompresses eligible opaque raster images at JPEG quality 82 and limits the longest side to 2,400 pixels | Keeps text and vectors editable; raster images may lose detail |
| `extreme` | Recompresses eligible opaque raster images at JPEG quality 60 and limits the longest side to 1,400 pixels | Stronger raster quality loss for smaller files |

Masked, stencil, and transparent images are retained unchanged so compression
does not destroy their compositing behavior. Shared images are processed once,
including images nested in bounded form resources.

The worker saves a candidate with PDFBox's compressed object-stream mode. The
candidate is published only when it is strictly smaller than the input.
Otherwise, the exact original bytes are returned under the compressed output
filename. The jobs response exposes input and output byte counts, and the
frontend reports the resulting percentage reduction.

## Fidelity

Compression preserves page count, media and crop boxes, rotation, `/UserUnit`,
text, vectors, and document structure. Recommended and extreme modes convert
eligible raster images to RGB JPEG, so color profiles, high-frequency detail,
and print fidelity can change. They do not rasterize whole pages.

A rewritten PDF invalidates existing digital signatures and may discard
linearization. Encrypted PDFs must be unlocked first. No font subsetting,
duplicate-resource consolidation, or commercial optimizer parity is claimed.

## Isolation and limits

Parsing, image decoding, recompression, and writing run in a killable Java
subprocess with a 512 MiB heap and five-minute wall timeout. Defaults:

- 100 MiB input, 128 MiB output, and 500 pages;
- 500 unique images and 10,000 image/form resources;
- 20,000,000 pixels per image and 200,000,000 aggregate image pixels;
- 8,192 pixels per input image side;
- 32 MiB per temporary JPEG and 256 MiB aggregate recompressed image bytes;
- resource nesting depth of 32;
- bounded page-tree nodes, depth, and content streams.

The parent reloads the result and verifies its PDF structure, page count, page
boxes, rotation, and `/UserUnit` before publishing it. Cancellation terminates
the worker and deletes partial output.
