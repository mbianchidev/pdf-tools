# PowerPoint to PDF

Operation key: `powerpoint-to-pdf`

PowerPoint to PDF converts one `.pptx` or legacy `.ppt` presentation with
LibreOffice Impress and returns one PDF page per slide.

## Request

```text
operation=powerpoint-to-pdf
options={"outputFilename":"slides.pdf"}
files=<slides.pptx>
```

`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.
PPTX archives use the shared bounded OOXML validator: required presentation
parts, traversal paths, macro payloads, archive entries, and expanded bytes are
checked before conversion. Legacy PPT inputs must have the OLE compound-document
signature.

## Isolation

Conversion uses the same hardened Office sidecar as Word to PDF. The sidecar has
no network or backend credentials, receives requests/responses/signals over
one-way volumes, writes conversion scratch only to a size-limited tmpfs, and runs
LibreOffice under a separate non-root identity. Seccomp, Landlock, native resource
limits, PID headroom, process-group cleanup, output bounds, cancellation, and
queue retention apply to every presentation.

## Fidelity

Slide order, common text, vector shapes, charts, tables, and embedded images are
rendered by LibreOffice Impress. Animations, transitions, audio, video, speaker
notes, and interactive actions are not represented in PDF. Missing fonts and
proprietary PowerPoint layout behavior can change wrapping, positioning, or
rendering. The result is validated as a readable, non-empty, unencrypted PDF.

LibreOffice is distributed under MPL/LGPL terms. No Microsoft PowerPoint or
commercial conversion SDK is bundled, and no pixel-identical parity is claimed.
