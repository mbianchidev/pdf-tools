import axios from 'axios';

// Use environment variable or default to production setup (nginx proxy)
const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/pdf';

// Create axios instance without Content-Type header
// Axios will automatically set multipart/form-data with boundary for FormData
const api = axios.create({
  baseURL: API_BASE_URL,
});

// Helper function to perform operation and then download the result
const performOperationAndDownload = async (endpoint, formData, originalFilename) => {
  // Add original filename to formData if provided
  if (originalFilename) {
    formData.append('originalFilename', originalFilename);
  }
  
  // First, call the operation endpoint to get the result with filename
  const operationResponse = await api.post(endpoint, formData);
  const result = operationResponse.data;
  
  if (!result.success) {
    throw new Error(result.message || 'Operation failed');
  }
  
  // Then download the file using the filename from the result
  const downloadResponse = await api.get(`/download/${result.outputFilename}`, {
    responseType: 'blob',
  });
  
  return downloadResponse.data;
};

export const pdfService = {
  // Merge multiple PDFs
  merge: async (files) => {
    const formData = new FormData();
    files.forEach((file) => {
      formData.append('files', file);
    });
    // Use first file's name as base for the merged output
    const originalFilename = files[0]?.name || 'document.pdf';
    return performOperationAndDownload('/merge', formData, originalFilename);
  },

  // Split PDF into individual pages or custom groups
  split: async (file, groups = null) => {
    const formData = new FormData();
    formData.append('file', file);
    if (groups) {
      formData.append('groups', groups);
    }
    formData.append('originalFilename', file.name);
    
    // Split returns multiple files as comma-separated filenames
    const operationResponse = await api.post('/split', formData);
    const result = operationResponse.data;
    
    if (!result.success) {
      throw new Error(result.message || 'Operation failed');
    }
    
    // The outputFilename contains comma-separated filenames
    const filenames = result.outputFilename.split(',');
    
    if (filenames.length > 1) {
      // Return info for multiple files
      return { filenames, message: result.message };
    } else {
      // Single file - download and return blob
      const downloadResponse = await api.get(`/download/${filenames[0]}`, {
        responseType: 'blob',
      });
      return { blob: downloadResponse.data, filenames };
    }
  },

  // Extract specific pages
  extractPages: async (file, pages) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('pages', pages);
    return performOperationAndDownload('/extract', formData, file.name);
  },

  // Remove specific pages
  removePages: async (file, pages) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('pages', pages);
    return performOperationAndDownload('/remove', formData, file.name);
  },

  // Add watermark with positioning
  addWatermark: async (file, text, x = null, y = null, rotation = 45, opacity = 0.3) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('text', text);
    if (x !== null) formData.append('x', x);
    if (y !== null) formData.append('y', y);
    formData.append('rotation', rotation);
    formData.append('opacity', opacity);
    return performOperationAndDownload('/watermark', formData, file.name);
  },

  // Add text to PDF with font customization
  addText: async (file, text, x, y, page, fontSize = 12, fontName = 'HELVETICA', fontColor = '#000000') => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('text', text);
    formData.append('x', x);
    formData.append('y', y);
    formData.append('page', page);
    formData.append('fontSize', fontSize);
    formData.append('fontName', fontName);
    formData.append('fontColor', fontColor);
    return performOperationAndDownload('/add-text', formData, file.name);
  },

  // Add signature to PDF
  addSignature: async (file, signature, x, y, page) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('signature', signature);
    formData.append('x', x);
    formData.append('y', y);
    formData.append('page', page);
    return performOperationAndDownload('/add-signature', formData, file.name);
  },

  // Redact content (single)
  redact: async (file, x, y, width, height, page) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('x', x);
    formData.append('y', y);
    formData.append('width', width);
    formData.append('height', height);
    formData.append('page', page);
    return performOperationAndDownload('/redact', formData, file.name);
  },

  // Redact multiple areas
  redactMultiple: async (file, redactions) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('redactions', JSON.stringify(redactions));
    return performOperationAndDownload('/redact-multiple', formData, file.name);
  },

  // Convert to Markdown
  convertToMarkdown: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return performOperationAndDownload('/convert/markdown', formData, file.name);
  },

  // Convert to DOCX
  convertToDocx: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return performOperationAndDownload('/convert/docx', formData, file.name);
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
  
  try {
    document.body.appendChild(link);
    link.click();
  } finally {
    // Ensure cleanup happens even if click fails
    document.body.removeChild(link);
    // Use setTimeout to ensure the download has started before revoking
    setTimeout(() => {
      window.URL.revokeObjectURL(url);
    }, 100);
  }
};
