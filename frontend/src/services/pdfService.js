import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/pdf';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'multipart/form-data',
  },
});

export const pdfService = {
  // Merge multiple PDFs
  merge: async (files) => {
    const formData = new FormData();
    files.forEach((file) => {
      formData.append('files', file);
    });
    const response = await api.post('/merge', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Split PDF into individual pages
  split: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post('/split', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Extract specific pages
  extractPages: async (file, pages) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('pages', pages);
    const response = await api.post('/extract', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Remove specific pages
  removePages: async (file, pages) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('pages', pages);
    const response = await api.post('/remove', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Add watermark
  addWatermark: async (file, text) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('text', text);
    const response = await api.post('/watermark', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Add text to PDF
  addText: async (file, text, x, y, page) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('text', text);
    formData.append('x', x);
    formData.append('y', y);
    formData.append('page', page);
    const response = await api.post('/add-text', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Add signature to PDF
  addSignature: async (file, signature, x, y, page) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('signature', signature);
    formData.append('x', x);
    formData.append('y', y);
    formData.append('page', page);
    const response = await api.post('/add-signature', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Redact content
  redact: async (file, x, y, width, height, page) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('x', x);
    formData.append('y', y);
    formData.append('width', width);
    formData.append('height', height);
    formData.append('page', page);
    const response = await api.post('/redact', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Convert to Markdown
  convertToMarkdown: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post('/convert/markdown', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Convert to DOCX
  convertToDocx: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post('/convert/docx', formData, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Download file
  download: async (filename) => {
    const response = await api.get(`/download/${filename}`, {
      responseType: 'blob',
    });
    return response.data;
  },
};

// Helper function to trigger file download
export const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};
