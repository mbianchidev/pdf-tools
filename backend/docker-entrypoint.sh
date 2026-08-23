#!/bin/sh
set -eu

volume_root=/tmp/pdf-storage
legacy_root=${PDF_UPLOAD_DIR:-"$volume_root/legacy"}
jobs_root=${PDF_STORAGE_LOCAL_ROOT:-"$volume_root/jobs"}
work_root=${PDF_JOB_WORK_ROOT:-/tmp/pdf-work}
multipart_root=${PDF_MULTIPART_TEMP_DIR:-/tmp/pdf-multipart}

mkdir -p "$legacy_root" "$jobs_root" "$work_root" "$multipart_root"

# Volumes created by older images stored legacy artifacts at the volume root.
if [ "$legacy_root" = "$volume_root/legacy" ] && [ "$jobs_root" = "$volume_root/jobs" ]; then
    for path in "$volume_root"/*; do
        case "$path" in
            "$legacy_root"|"$jobs_root")
                ;;
            *)
                if [ -e "$path" ]; then
                    mv "$path" "$legacy_root"/
                fi
                ;;
        esac
    done
fi

chown -R pdftools:pdftools "$legacy_root" "$jobs_root" "$work_root" "$multipart_root"

case "${1:-}" in
    ""|-*)
        set -- java -jar /app/app.jar "$@"
        ;;
esac

exec gosu pdftools "$@"
