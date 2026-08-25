# Excel to PDF

Operation key: `excel-to-pdf`

Excel to PDF converts one `.xlsx` or legacy `.xls` workbook with LibreOffice
Calc. Print-area and orientation settings are applied before conversion.

## Options

```json
{
  "printAreaMode": "custom",
  "printArea": "A1:F40",
  "orientation": "landscape",
  "outputFilename": "workbook.pdf"
}
```

- `printAreaMode` is `existing` (default), `used`, or `custom`.
- `existing` preserves workbook print areas.
- `used` computes each worksheet's populated-cell rectangle.
- `custom` requires one contiguous A1 `printArea` without a sheet name and
  applies it to every worksheet.
- `orientation` is `preserve` (default), `portrait`, or `landscape` and applies
  to every worksheet.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

XLSX archives use the shared bounded OOXML validator. Macro declarations are
rejected by parsing content types and relationships with DTDs and external
entities disabled. Legacy XLS files must contain an Excel `Workbook` or `Book`
compound-file stream. Password-protected workbooks are rejected.

## Preparation and isolation

Workbook preparation does not evaluate formulas or fetch external data. It
loads at most 100 sheets, scans at most one million physical cells in every
print-area mode, and writes at most 100 MiB of prepared workbook data. POI
preparation and LibreOffice conversion run as separate bounded processes under
the sidecar's non-root worker identity. The same networkless, one-way-volume
Office boundary used by Word and PowerPoint applies, including seccomp,
Landlock, tmpfs scratch, native limits, PID headroom, process cleanup, output
bounds, cancellation, and queue retention. The POI worker has a 512 MiB Java
heap, bounded JVM native regions, and a 4 GiB virtual-address ceiling; the
sidecar's 1.2 GiB cgroup remains the physical-memory ceiling.

## Fidelity

LibreOffice Calc preserves common values, formulas, formatting, charts, images,
sheet order, and page settings. Unsupported formulas, unavailable fonts,
external data sources, macros, hidden content behavior, and proprietary Excel
layout can differ. The PDF may contain multiple pages per worksheet depending
on print area, scale, margins, and page size. No Microsoft Excel or commercial
conversion SDK is bundled, and no pixel-identical parity is claimed.
