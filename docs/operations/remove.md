# Remove Pages

Operation key: `remove`

Remove Pages accepts one PDF and returns one PDF containing every page not selected
for removal, in original order.

## Options

```json
{
  "pages": "2,4-6,9-",
  "outputFilename": "report_pages_removed.pdf"
}
```

`pages` is required and uses the shared page-expression grammar: pages, closed or
open ranges, `odd`, `even`, and `all`. Duplicate selections are rejected. Removing
every page is rejected because a valid output must retain at least one page.

`outputFilename` is optional. When provided, it must end in `.pdf` and remain
within 120 UTF-8 bytes.

## Validation and errors

- exactly one non-empty PDF input
- encrypted PDFs rejected until unlocked
- source, decoded-content, filter, page-tree, resource, scratch, and output limits
  are inherited from the hardened page-copy engine
- malformed, descending, duplicate, and out-of-range page expressions are rejected
- `all` or any equivalent all-page expression returns `CANNOT_REMOVE_ALL_PAGES`

Common structured errors include `REMOVE_PAGES_REQUIRED`, `INVALID_REMOVE_PAGES`,
`DUPLICATE_PAGE`, `PAGE_OUT_OF_RANGE`, `DESCENDING_PAGE_RANGE`,
`CANNOT_REMOVE_ALL_PAGES`, and the bounded PDF/resource errors documented for
[Split PDF](split.md).

## Resource and fidelity behavior

The output uses the same deterministic, disk-backed, bounded page reconstruction as
Split PDF. Page boxes, rotation, selected-page content, fonts, images, color spaces,
shadings, and safe transparency metadata are preserved. Annotations/actions and
document-level outlines, page labels, and attachments are removed so deleted-page
content cannot remain reachable. Optional-content layers, Type 3 fonts, used
graphics-state soft masks, and non-device transparency-group color spaces fail
explicitly rather than being silently altered.

The legacy `/api/pdf/remove` endpoint accepts the same page-expression string, runs on
the isolated legacy PDF executor, streams one resulting PDF, and shares two-hour
artifact expiry.
