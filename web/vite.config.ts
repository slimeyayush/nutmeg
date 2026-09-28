import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev:   npm run dev  -> http://localhost:5173, proxying /api and /ws to Spring on 8080.
// Build: npm run build -> writes straight into Spring's static folder, so
//        ./gradlew bootRun serves the whole app from http://localhost:8080.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: true,            // lets phones on your wifi open http://<your-ip>:5173
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
