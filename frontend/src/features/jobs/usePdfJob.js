import { useCallback, useEffect, useRef, useState } from 'react';
import { jobService } from '../../services/jobService';

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

export const usePdfJob = () => {
  const [job, setJob] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [connectionError, setConnectionError] = useState(false);
  const closeSubscription = useRef(null);
  const abortRequest = useRef(null);
  const generation = useRef(0);
  const latestJob = useRef(null);

  const close = useCallback(() => {
    closeSubscription.current?.();
    closeSubscription.current = null;
  }, []);

  const acceptSnapshot = useCallback((nextJob) => {
    const current = latestJob.current;
    if (
      current
      && current.id === nextJob.id
      && (nextJob.version ?? 0) <= (current.version ?? 0)
    ) {
      return false;
    }
    latestJob.current = nextJob;
    setJob(nextJob);
    return true;
  }, []);

  useEffect(() => () => {
    generation.current += 1;
    abortRequest.current?.abort();
    close();
    latestJob.current = null;
    setJob(null);
  }, [close]);

  const start = useCallback(async (operation, files, options = {}) => {
    const requestGeneration = generation.current + 1;
    generation.current = requestGeneration;
    abortRequest.current?.abort();
    close();
    const controller = new AbortController();
    abortRequest.current = controller;
    setSubmitting(true);
    setConnectionError(false);
    try {
      const created = await jobService.create(operation, files, options, controller.signal);
      if (generation.current !== requestGeneration) {
        return null;
      }
      acceptSnapshot(created);
      if (TERMINAL_STATUSES.has(created.status)) {
        return created;
      }
      closeSubscription.current = jobService.subscribe(
        created.id,
        (nextJob) => {
          if (generation.current !== requestGeneration) {
            return;
          }
          const accepted = acceptSnapshot(nextJob);
          setConnectionError(false);
          if (TERMINAL_STATUSES.has(nextJob.status)) {
            close();
          }
          if (!accepted) {
            return;
          }
        },
        () => {
          if (generation.current === requestGeneration) {
            setConnectionError(true);
          }
        },
      );
      return created;
    } finally {
      if (generation.current === requestGeneration) {
        abortRequest.current = null;
        setSubmitting(false);
      }
    }
  }, [acceptSnapshot, close]);

  const cancel = useCallback(async () => {
    if (!job || TERMINAL_STATUSES.has(job.status)) {
      return;
    }
    const cancelledJobId = job.id;
    const requestGeneration = generation.current;
    const cancelled = await jobService.cancel(cancelledJobId);
    if (generation.current === requestGeneration && cancelled.id === cancelledJobId) {
      const accepted = acceptSnapshot(cancelled);
      if (accepted && TERMINAL_STATUSES.has(cancelled.status)) {
        close();
      }
    }
  }, [acceptSnapshot, close, job]);

  const reset = useCallback(() => {
    generation.current += 1;
    abortRequest.current?.abort();
    abortRequest.current = null;
    close();
    latestJob.current = null;
    setJob(null);
    setSubmitting(false);
    setConnectionError(false);
  }, [close]);

  return {
    job,
    submitting,
    connectionError,
    running: submitting || Boolean(job && !TERMINAL_STATUSES.has(job.status)),
    start,
    cancel,
    reset,
  };
};
