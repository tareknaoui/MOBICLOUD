import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/relay': {
        target: 'https://mobicloud-relay-3.onrender.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/relay/, ''),
        headers: {
          Connection: 'keep-alive',
        },
      },
    },
  },
})
