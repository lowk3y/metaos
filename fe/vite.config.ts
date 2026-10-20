import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'

// MetaOS, which the dev server passes the API on to; METAOS_URL points it at another instance than the one on :8080
const metaos = process.env.METAOS_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // Through the proxy the browser sees one origin, as in production, where MetaOS serves fe/
    proxy: {
      '/graphql': metaos,
      '/config.json': metaos,
      // Thumbnails of the photos in Koofr folders (KoofrPhotos.kt)
      '/koofr': metaos,
    },
  },
})
