import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  // Load .env.* files from collectory-ui/config/ instead of the project root.
  // This matches the species-lists pattern where per-env files live in config/.
  envDir: 'config',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/rif-cs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/feed': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/eml': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/data': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/upload': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
