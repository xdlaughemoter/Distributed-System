import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {},
  },
  css: {},
  server: {
    
    host: '0.0.0.0',
    port: 80,
    strictPort: true, // Fail if port 80 is taken instead of picking a random one
    open: true,
  },
  build: {
    outDir: 'dist',
  },
  optimizeDeps: {
    include: [],
  },
  esbuild: {
    jsxFactory: 'React.createElement',
    jsxFragment: 'React.Fragment',
  }
})
