# Jobs API v1

Base path: `/api/v1`

## Create a job

```http
POST /api/v1/jobs
Content-Type: multipart/form-data
```

Multipart fields:

| Field | Required | Description |
| --- | --- | --- |
| `operation` | Yes | Enabled kebab-case key, limited to 64 UTF-8 bytes |
| `options` | No | JSON object; defaults to `{}` and is limited to 64 KiB |
| `files` | Yes | One or more ordered input files |

The API returns `202 Accepted`, a `Location` header, and the job representation.
Inputs preserve multipart order.
The deployment accepts at most 100 MB per multipart request and 100 MB per file.

```json
{
  "id": "1c6bf2ad-2f56-4ff6-bc5a-737ca38fe8b4",
  "operation": "merge",
  "status": "RUNNING",
  "version": 2,
  "progress": 25,
  "message": "Processing",
  "errorCode": null,
  "errorMessage": null,
  "cancellationRequested": false,
  "createdAt": "2026-08-23T09:00:00Z",
  "updatedAt": "2026-08-23T09:00:01Z",
  "expiresAt": "2026-08-23T11:00:00Z",
  "outputs": []
}
```

## Read a job

```http
GET /api/v1/jobs/{jobId}
```

Terminal statuses are `COMPLETED`, `FAILED`, and `CANCELLED`. Completed jobs expose
ordered `outputs[]` entries with a `downloadUrl`, size, media type, checksum, and expiry.
After `expiresAt`, job reads and downloads return `410 Gone` even if background storage
cleanup has not completed.

## Stream progress

```http
GET /api/v1/jobs/{jobId}/events
Accept: text/event-stream
```

The stream sends named `job` events containing the full current representation. It
closes after a terminal state. Clients should fall back to `GET /jobs/{jobId}` if the
connection is interrupted.
Expired jobs are rejected before streaming with `410` and `X-Error-Code: JOB_EXPIRED`.

`version` is monotonic. Clients must ignore snapshots whose version is not newer than
the latest state they already applied.

## Cancel a job

```http
DELETE /api/v1/jobs/{jobId}
```

The API returns `202 Accepted`. Pending work is cancelled immediately. Running
operations observe cancellation at progress and I/O checkpoints.

## Download an output

```http
GET /api/v1/jobs/{jobId}/outputs/{outputId}
```

The response streams the artifact with its stored media type, content length,
UTF-8-safe attachment filename, and `Cache-Control: private, no-store`.

## Errors

All API errors use one shape:

```json
{
  "timestamp": "2026-08-23T09:00:00Z",
  "status": 422,
  "code": "PAGE_OUT_OF_RANGE",
  "message": "Page 8 is outside the valid range 1-4",
  "path": "/api/v1/jobs",
  "details": {
    "token": "8"
  }
}
```

Expected status codes include `400` for malformed requests, `404` for disabled or
missing resources, `409` for invalid lifecycle transitions, `413` for configured
limits, and `422` for operation-specific validation.

## Page expressions

The shared grammar accepts:

- pages: `1,3,8`
- closed ranges: `2-5`
- open ranges: `-4`, `7-`
- keywords: `all`, `odd`, `even`

Operations select one of three duplicate policies: keep, deduplicate, or reject.
Descending ranges, empty tokens, page zero, and out-of-range pages are invalid.
An expression may expand to at most 100,000 page selections.

## Merge PDF

`operation=merge` accepts 2-20 ordered PDF inputs and an optional
`options.outputFilename`. See [Merge PDF](operations/merge.md) for validation, resource,
and fidelity details.

## Split PDF

`operation=split` accepts one PDF and supports `individual`, `ranges`, and `fixed`
modes. It returns one ZIP artifact. See [Split PDF](operations/split.md) for options,
limits, and fidelity details.
