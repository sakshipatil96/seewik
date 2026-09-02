const DEPLOYED_API_URL = 'https://seewik-api-528138216934.asia-south1.run.app';

const localBrowser = typeof window !== 'undefined'
  && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

// Local Vite servers proxy same-origin /api requests to Cloud Run. This keeps
// development reliable even when Vite selects a different local port.
export const API_URL = import.meta.env.VITE_API_URL
  || (localBrowser ? '' : DEPLOYED_API_URL);
