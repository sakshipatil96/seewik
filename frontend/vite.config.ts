import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

const createApiProxy = (apiTarget: string) => ({
  '/api': {
    target: apiTarget,
    changeOrigin: true,
    secure: apiTarget.startsWith('https://'),
    configure(proxy) {
      // The browser talks same-origin to Vite. The onward request is
      // server-to-server, so do not forward the browser's localhost Origin.
      proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'));
    },
  },
});

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080';
  const apiProxy = createApiProxy(apiTarget);

  return {
    plugins: [react()],
    server: { proxy: apiProxy },
    preview: { proxy: apiProxy },
  };
});
