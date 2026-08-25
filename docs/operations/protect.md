# Protect PDF

Operation key: `protect`

Protect PDF accepts one unencrypted PDF and returns one AES-256 encrypted PDF.
It requires separate open and owner passwords and applies explicit user permissions.

## Options

```json
{
  "userPassword": "open-secret",
  "ownerPassword": "owner-secret",
  "permissions": {
    "print": "low",
    "copy": false,
    "modify": false,
    "annotate": true,
    "fillForms": true,
    "accessibility": true,
    "assemble": false
  },
  "outputFilename": "protected.pdf"
}
```

- Both passwords are required, must differ after PDFBox password preparation, contain
  printable ASCII only, and may contain at most 127 bytes.
- `print` is `none`, `low`, or `high`.
- Other permission values are booleans and default to least privilege, except
  accessibility extraction which defaults to enabled.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.

Existing encrypted PDFs are rejected; use Unlock PDF first when the password is known.

## Sensitive job options

Protect marks its job options as sensitive. Before metadata is persisted, the complete
options JSON is encrypted with AES-256-GCM and a random nonce. The local single-node
default creates a 32-byte key at `/tmp/pdf-storage/.options-key` with owner-only POSIX
permissions. Set `PDF_OPTIONS_ENCRYPTION_KEY` to a base64-encoded 32-byte key for
multi-replica or production deployments so every worker can decrypt queued jobs after
restart. Back up and rotate this key according to the operator's secret-management policy.

Passwords are never returned by the jobs API or written to application logs.

## Fidelity and limits

Protection preserves the source document structure and content rather than rebuilding
pages. PDFBox uses a 256-bit standard security policy with AES preferred. Input parsing
uses disk-backed scratch storage and output writes are byte-bounded and cancellation-aware.
Permission flags are viewer-enforced PDF restrictions, not digital rights management;
software with owner credentials can change them.
