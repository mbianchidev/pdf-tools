const OBJECT_URL_REVOCATION_DELAY_MS = 100;

export const startBrowserDownload = (url, filename) => {
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.style.display = 'none';

  try {
    document.body.appendChild(link);
    link.click();
  } finally {
    link.remove();
    if (typeof url === 'string' && url.startsWith('blob:')) {
      setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_REVOCATION_DELAY_MS);
    }
  }
};
