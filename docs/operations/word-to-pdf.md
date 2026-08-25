# Word to PDF

Operation key: `word-to-pdf`

Word to PDF converts one `.docx` or legacy `.doc` file with LibreOffice Writer
and returns one PDF.

## Request

```text
operation=word-to-pdf
options={"outputFilename":"report.pdf"}
files=<report.docx>
```

`outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.
Inputs default to a 50 MiB byte limit. DOCX archives are streamed and checked
for required Word parts, traversal paths, macro payloads, 10,000 entries, and
256 MiB of expanded content before LibreOffice starts. Legacy DOC inputs must
have the OLE compound-document signature.

## Isolation

The default Docker deployment sends a fixed binary request through three one-way
volumes to a dedicated `office-converter` container. The backend can write
requests and cancellation/acknowledgement signals but mounts responses read-only;
the sidecar mounts requests/signals read-only and responses read-write. Neither
side can race the other's protocol writes. The converter container has no network,
database configuration, backend environment, artifact storage, or job-workspace
mounts. A small supervisor owns the queue, while LibreOffice runs under the
separate non-root `officeworker` identity and cannot signal that supervisor.
Conversion scratch lives on a 768 MiB sidecar-only tmpfs; only the bounded,
validated PDF reaches the persistent response volume. Container memory, CPU, and
PID ceilings apply as an outer limit.

Each conversion inside that container receives a private input copy, temporary
directory, home, XDG directories, and LibreOffice user profile. The environment
is cleared and replaced with a small allowlist. The profile sets macro security
to its highest level and suppresses first-run UI.

The Linux native process additionally uses:

- a seccomp filter that permits only Unix-domain sockets and denies new IP or
  other socket families;
- Landlock rules that allow read-only system files plus `/proc` introspection,
  permit LibreOffice's Unix socket in the otherwise empty sidecar `/tmp`, and
  grant full access only inside the current queue request;
- `no_new_privs` with inheritable and ambient capabilities cleared;
- address-space, CPU-time, output-file, and open-file limits plus a container
  PID ceiling and a lower worker-UID process limit that reserves supervisor
  termination headroom;
- non-root execution plus a wall-time timeout and process-tree termination.

Explicit `OFFICE_CONVERSION_MODE=direct` is available for macOS development.
That mode uses Seatbelt to deny IP networking while allowing the local
Unix sockets LibreOffice needs, plus CPU, output-file, and open-file limits; it
does not isolate filesystem reads and is intended only for trusted local fixtures.
Linux direct mode and unsupported platforms fail closed.

## Fidelity

LibreOffice preserves common paragraphs, styles, tables, images, headers,
footers, page breaks, and pagination. The result depends on LibreOffice's import
filters and the fonts installed on the server. Missing fonts may be substituted,
changing line breaks or page count. Complex fields, embedded objects, tracked
changes, legacy DOC features, and proprietary Microsoft Word layout behavior
can differ.

The output is bounded to 128 MiB and validated as a readable, non-empty,
unencrypted PDF. LibreOffice is distributed under MPL/LGPL terms; no Microsoft
Word or commercial conversion SDK is bundled, and no pixel-identical parity is
claimed.
