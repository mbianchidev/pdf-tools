CREATE TABLE pdf_jobs (
    id UUID PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    message TEXT,
    error_code VARCHAR(96),
    error_message TEXT,
    options_json TEXT NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    worker_id VARCHAR(64),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pdf_jobs_progress_range CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT pdf_jobs_status_valid CHECK (
        status IN ('UPLOADING', 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE TABLE pdf_job_inputs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES pdf_jobs(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    storage_key TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    media_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pdf_job_inputs_size_nonnegative CHECK (size_bytes >= 0),
    CONSTRAINT pdf_job_inputs_position_unique UNIQUE (job_id, position),
    CONSTRAINT pdf_job_inputs_storage_key_unique UNIQUE (storage_key)
);

CREATE TABLE pdf_job_outputs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES pdf_jobs(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    storage_key TEXT NOT NULL,
    filename TEXT NOT NULL,
    media_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pdf_job_outputs_size_nonnegative CHECK (size_bytes >= 0),
    CONSTRAINT pdf_job_outputs_position_unique UNIQUE (job_id, position),
    CONSTRAINT pdf_job_outputs_storage_key_unique UNIQUE (storage_key)
);

CREATE INDEX idx_pdf_jobs_expires_at ON pdf_jobs (expires_at);
CREATE INDEX idx_pdf_jobs_status_updated_at ON pdf_jobs (status, updated_at);
CREATE INDEX idx_pdf_jobs_status_lease ON pdf_jobs (status, lease_expires_at);
CREATE INDEX idx_pdf_job_inputs_job_id ON pdf_job_inputs (job_id);
CREATE INDEX idx_pdf_job_outputs_job_id ON pdf_job_outputs (job_id);
