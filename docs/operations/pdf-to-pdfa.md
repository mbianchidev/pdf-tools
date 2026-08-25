# PDF to PDF/A

Operation key: `pdf-to-pdfa`

PDF to PDF/A accepts one unencrypted PDF, converts it with LibreOffice Draw in
the isolated Office sidecar, and publishes it only after a separate veraPDF
worker confirms the selected conformance profile.

## Options

```json
{
  "profile": "pdfa-2b",
  "outputFilename": "document-pdfa-2b.pdf"
}
```

- `profile` is `pdfa-1b`, `pdfa-2b` (default), or `pdfa-3b`.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

Only B-level profiles are offered. The converter does not claim the tagged
structure required by A-level conformance or the Unicode mapping required by
U-level conformance.

## Profiles

| Profile | Base | Use |
| --- | --- | --- |
| PDF/A-1b | PDF 1.4 | Broad compatibility; excludes later PDF features |
| PDF/A-2b | PDF 1.7 | Recommended modern baseline |
| PDF/A-3b | PDF 1.7 | Adds the standard's embedded-file capability |

PDF/A-3b conformance permits attachments, but this implementation does not
promise to preserve source attachments through LibreOffice Draw import.

## Conversion and validation

The two independent stages are:

1. LibreOffice Draw imports the source PDF and exports the requested
   `SelectPdfVersion` in the networkless Office sidecar.
2. veraPDF 1.30.2 runs in a separate Java process against the exact output and
   exact requested flavour (`PDFA_1_B`, `PDFA_2_B`, or `PDFA_3_B`).

A non-compliant candidate fails with `PDFA_VALIDATION_FAILED` and is deleted.
Successful jobs return two ordered artifacts:

1. the validated PDF/A document;
2. a JSON validation report.

```json
{
  "status": "compliant",
  "profile": "pdfa-2b",
  "compliant": true,
  "totalAssertions": 142,
  "failedChecks": 0,
  "failures": []
}
```

## Fidelity

LibreOffice Draw reimports and reexports the source. Fonts, forms, annotations,
links, layers, transparency, color management, metadata, signatures, and layout
can change or be removed. Existing digital signatures are invalidated.
veraPDF conformance proves the tested archival rules, not visual parity,
semantic completeness, accessibility, or preservation of every source object.

No commercial PDF/A conversion parity is claimed.

## Isolation and limits

The conversion inherits the Office sidecar's non-root identity, no-network
policy, private profile/scratch, 1 GiB address-space cap, native CPU/file/PID
limits, and two-minute wall timeout. The backend additionally enforces:

- 50 MiB input and 128 MiB output;
- 200 pages;
- bounded page-tree nodes, depth, and content streams.

veraPDF runs in a killable Java worker with:

- a 512 MiB heap and five-minute wall timeout;
- a 100-rule failure-detail cap;
- 500 characters per failure field;
- a 256 KiB report limit.

Cancellation terminates conversion or validation and removes both candidate and
report.

## Licensing

LibreOffice is the separate conversion engine already distributed by the
project. veraPDF `validation-model-jakarta` 1.30.2 is used under the Mozilla
Public License 2.0 option of its dual MPL-2.0/GPL-3.0-or-later license. The
Greenfield parser is used; the retired PDFBox veraPDF model is not bundled.
