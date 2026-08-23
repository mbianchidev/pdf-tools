import { X } from 'lucide-react';
import './JobProgress.css';

const JobProgress = ({ job, connectionError = false, onCancel }) => {
  if (!job) {
    return null;
  }

  const canCancel = job.status === 'PENDING' || job.status === 'RUNNING';

  return (
    <section className="job-progress" aria-live="polite">
      <div className="job-progress-header">
        <div>
          <strong>{job.message || 'Processing'}</strong>
          <span>{job.progress}%</span>
        </div>
        {canCancel && onCancel && (
          <button type="button" onClick={onCancel} aria-label="Cancel PDF job">
            <X size={16} />
            Cancel
          </button>
        )}
      </div>
      <progress max="100" value={job.progress} aria-label="PDF job progress">
        {job.progress}%
      </progress>
      {connectionError && canCancel && (
        <p className="job-progress-warning">
          Live updates were interrupted. The job continues on the server.
        </p>
      )}
      {job.status === 'FAILED' && (
        <p className="job-progress-error">
          {job.errorMessage || 'The PDF job failed.'}
        </p>
      )}
    </section>
  );
};

export default JobProgress;
