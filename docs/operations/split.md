# Split PDF

Operation key: `split`

Split PDF accepts one document and returns one deterministic ZIP containing every
generated PDF in output order.

## Modes

### Individual pages

```json
{ "mode": "individual" }
```

Creates one PDF per page with names such as `source_page_0001.pdf`.

### Page ranges

```json
{
  "mode": "ranges",
  "ranges": ["1-3", "4,6", "7-"]
}
```

Each page expression creates one output document. Expressions use the shared grammar
(`all`, `odd`, `even`, pages, closed ranges, and open ranges). Pages may be omitted,
but duplicate pages inside a range or overlaps across ranges are rejected.

### Fixed-size groups

```json
{
  "mode": "fixed",
  "fixedGroupSize": 5
}
```

Creates consecutive groups of up to the requested page count. The final group may be
smaller.

## Limits and validation

- exactly one non-empty PDF input
- encrypted PDFs rejected until unlocked
- at most 1,000 input pages
- at most 500 output documents
- fixed group size between 1 and 500
- at most 128 MB per generated PDF
- at most 512 MB across generated PDFs
- at most 32 MB decoded page content and 256 MB decoded content per job
- at most 1,000 content streams per page
- at most 512 MB of aggregate resource-copy scratch writes
- at most 250,000 lexical and nested content objects, 32 nested resource levels,
  10,000 nodes per resource graph, and 1,000,000 resource nodes per job
- optional-content layers are rejected
- optional ZIP filename ending in `.zip` and limited to 120 UTF-8 bytes

Structured errors include `INVALID_SPLIT_MODE`, `SPLIT_RANGES_REQUIRED`,
`INVALID_SPLIT_RANGE`, `OVERLAPPING_SPLIT_RANGES`, `INVALID_FIXED_GROUP_SIZE`,
`PDF_PAGE_LIMIT_EXCEEDED`, `SPLIT_OUTPUT_LIMIT_EXCEEDED`,
`PDF_CONTENT_COMPLEXITY_LIMIT_EXCEEDED`, `PDF_RESOURCE_COMPLEXITY_LIMIT_EXCEEDED`,
`OPTIONAL_CONTENT_UNSUPPORTED`, `UNSUPPORTED_TRANSPARENCY_GROUP`,
`UNSUPPORTED_TRANSPARENCY_GROUP_COLOR_SPACE`, `UNSUPPORTED_TYPE3_FONT`,
`UNSUPPORTED_SOFT_MASK`, `UNSAFE_RESOURCE_REFERENCE`, and
`INVALID_PDF_PAGE_TREE`. Oversized page content arrays return
`PDF_CONTENT_STREAM_LIMIT_EXCEEDED`; resource-copy amplification returns
`SPLIT_RESOURCE_SCRATCH_LIMIT_EXCEEDED`.

## Resource behavior

The source uses disk-backed PDFBox random access and workspace-local scratch storage.
Filtered page, form, and pattern content is decoded one stage at a time into disk-backed,
byte-bounded streams before PDFBox parses it. Outputs are saved while the source remains
open so stream-backed fonts, images, and appearance resources remain readable.
Cancellation is checked at bounded page
and byte intervals. Every write is byte-bounded, parts are deleted after being added to
the ZIP, and partial artifacts are removed on failure. Deterministic PDF IDs plus stable
ZIP entry order, timestamps, and collision-safe names make identical inputs produce
identical ZIP bytes. Legacy processing runs on an isolated one-to-two-worker executor with
a four-request queue, a ten-minute deadline, disconnect cancellation, and explicit
overload responses; legacy ZIP downloads stream instead of buffering the archive.
Anonymous legacy artifacts expire on the same two-hour retention schedule as job artifacts.

## Fidelity

Each output preserves selected page content, resources, and page boxes. Pages are
rebuilt from a whitelist of page-local entries, and interactive annotations/actions are
removed so links, article threads, form parents, or destinations cannot retain omitted
page content. Transparency groups are rebuilt from their rendering flags and safe device
color-space names rather than copying arbitrary source dictionary entries. Document-level
outlines, page labels, and attachments are not copied.

Resource graphs are copied through bounded, cancellation-aware traversal. References back
to page dictionaries or page content are rejected. Used graphics-state soft masks are
rejected explicitly because stripping them would silently change rendered output.
The page tree is traversed directly with parent, cycle, depth, node, and page-count checks
before PDFBox can expand it. Type 3 fonts and transparency groups with non-device
color spaces are rejected explicitly rather than copied without complete sanitization.
