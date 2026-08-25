# Unlock PDF

Operation key: `unlock`

Unlock PDF accepts one password-encrypted PDF and returns one unencrypted PDF.
The supplied credential may be either the current user password or owner password.

## Options

```json
{
  "password": "known-password",
  "outputFilename": "unlocked.pdf"
}
```

- `password` is required, supports Unicode, and may contain at most 127 UTF-8 bytes.
- `outputFilename` is optional and must end in `.pdf` within 120 UTF-8 bytes.
- Unencrypted inputs and incorrect passwords are rejected explicitly.

## Sensitive job options

Unlock marks its complete options object as sensitive. The password is AES-256-GCM
encrypted before job metadata is persisted, is never returned by the jobs API, and
is not written to application logs. Shared-key configuration and local key handling
follow [Protect PDF](protect.md#sensitive-job-options).

For rolling multi-replica deployments, deploy the Unlock-capable binary to every
worker before adding `unlock` to `PDF_ENABLED_OPERATIONS`. Docker Compose enables
the completed operation by default.

## Fidelity and limits

PDFBox authenticates the supplied password, loads the complete document with
disk-backed scratch storage, removes the security handler, and writes a new bounded
PDF. The output is a full rewrite rather than an incremental update, so encrypted
prior revisions are not copied into the result.

Unlock removes password encryption and viewer-enforced permissions. A full rewrite
invalidates existing digital signatures even when their signature fields remain.
It does not remove certificate-based encryption, DRM from non-PDF formats, or
recover a forgotten password.
