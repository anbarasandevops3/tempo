import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';

// base './' so the build works from any sub-path (e.g. GitHub Pages /tempo/)
export default defineConfig({
  base: './',
  plugins: [preact()],
  test: { environment: 'node' },
});
