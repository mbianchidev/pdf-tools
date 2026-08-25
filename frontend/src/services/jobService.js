import axios from 'axios';

export const normalizeJobApiBaseUrl = (baseUrl) => baseUrl.replace(/\/+$/, '');

const JOB_API_BASE_URL = normalizeJobApiBaseUrl(
  import.meta.env.VITE_JOB_API_URL || '/api/v1',
);

const jobsApi = axios.create({
  baseURL: JOB_API_BASE_URL,
});

const resolveApiUrl = (path) => {
  const configuredBase = new URL(JOB_API_BASE_URL, window.location.origin);
  return new URL(path, configuredBase.origin).toString();
};

export const jobService = {
  create: async (operation, files, options = {}, signal) => {
    const formData = new FormData();
    formData.append('operation', operation);
    formData.append('options', JSON.stringify(options));
    files.forEach((file) => formData.append('files', file));
    const response = await jobsApi.post('/jobs', formData, { signal });
    return response.data;
  },

  get: async (jobId) => {
    const response = await jobsApi.get(`/jobs/${jobId}`);
    return response.data;
  },

  cancel: async (jobId) => {
    const response = await jobsApi.delete(`/jobs/${jobId}`);
    return response.data;
  },

  subscribe: (jobId, onJob, onError) => {
    const source = new EventSource(resolveApiUrl(`${JOB_API_BASE_URL}/jobs/${jobId}/events`));
    source.addEventListener('job', (event) => {
      onJob(JSON.parse(event.data));
    });
    source.onerror = (event) => {
      onError?.(event);
    };
    return () => source.close();
  },

  download: async (output) => {
    const response = await axios.get(resolveApiUrl(output.downloadUrl), {
      responseType: 'blob',
    });
    return response.data;
  },

  getDownloadUrl: (output) => resolveApiUrl(output.downloadUrl),
};

export const getApiErrorMessage = (error, fallback) => (
  error.response?.data?.message || error.message || fallback
);
