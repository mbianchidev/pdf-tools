# Repair PDF

Operation key: `repair`

Repair PDF accepts one damaged, unencrypted PDF and returns two ordered outputs:

1. a structurally rewritten PDF;
2. a JSON repair report that explicitly states whether recovery was complete or
   partial.

## Options

```json
{
  "outputFilename": "document-repaired.pdf"
}
```

`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes. The
report uses the corresponding `*-repair-report.json` name.

## Recovery model

The operation invokes qpdf to:

- locate or reconstruct damaged cross-reference information;
- rewrite objects and streams into a deterministic PDF;
- generate compressed object streams;
- run a second qpdf structural check;
- load the result with PDFBox and validate its page tree, content-stream shape,
  page count, encryption state, and output bounds.

qpdf exit status `3` is a successful result with warnings. Any rewrite or
post-check warning marks the result `partially-recovered`, even when the final
file passes structural validation. This is intentional: reconstruction may omit
objects that cannot be recovered.

## Repair report

The second output has this shape:

```json
{
  "status": "partially-recovered",
  "summary": "qpdf recovered the PDF with warnings; review the document for missing or altered content",
  "recoveredPages": 4,
  "warnings": [
    "WARNING: input.pdf: Attempting to reconstruct cross-reference table"
  ]
}
```

`status` is `repaired` or `partially-recovered`. Diagnostic paths are sanitized,
warning count and length are bounded, and the frontend reads the report before
showing the recovery result. The repaired PDF downloads automatically; the
report remains available as a separate artifact.

## Limits and security

qpdf runs as the backend's non-root user in a separate process with:

- outbound network denied by seccomp on Linux or Seatbelt on macOS;
- a 1 GiB virtual-address limit in production Linux containers;
- 120 CPU seconds;
- 128 open files;
- 100 MiB input and 128 MiB output limits;
- a 1 MiB aggregate stdout/stderr limit;
- two minutes for rewriting and one minute for post-validation;
- process termination and partial-output cleanup on cancellation.

macOS development uses Seatbelt network denial plus CPU, file-size, and
descriptor limits; deploy through Docker for the Linux address-space boundary.
Linux hosts whose `setpriv` lacks seccomp support fail closed. The explicit
`REPAIR_ALLOW_UNSANDBOXED_LINUX=true` escape hatch is reserved for trusted
synthetic tests; Docker always forces it off.

The repaired output is additionally limited to 1,000 pages with bounded page
tree depth, nodes, and content streams. Docker images include qpdf; local
development requires qpdf on `PATH` or `QPDF_BINARY`.

## Fidelity and licensing

Structural repair cannot recreate bytes, page content, fonts, images,
annotations, attachments, forms, or metadata that are absent or irrecoverably
damaged. A rewritten PDF invalidates existing digital signatures and may change
object numbers, compression, linearization, or byte-level forensic evidence.
Encrypted PDFs must be unlocked first.

qpdf is an Apache License 2.0 dependency. No commercial repair-engine parity is
claimed.
