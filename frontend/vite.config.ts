import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiTarget = 'https://seewik-api-528138216934.asia-south1.run.app';
const apiProxy = {
  '/api': {
    target: apiTarget,
    changeOrigin: true,
    secure: true,
    configure(proxy) {
      // The browser talks same-origin to Vite. The onward request is
      // server-to-server, so do not forward the browser's localhost Origin.
      proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'));
    },
  },
};

export default defineConfig({
  plugins: [react()],
  server: { proxy: apiProxy },
  preview: { proxy: apiProxy },
});
