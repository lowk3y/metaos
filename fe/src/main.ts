// Inter, served with the app rather than from Google
import '@fontsource-variable/inter'
import './assets/main.scss'
// Font Awesome's CSS comes with the bundle, rather than being added at runtime, so icons don't show unstyled first
import '@fortawesome/fontawesome-svg-core/styles.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { config } from '@fortawesome/fontawesome-svg-core'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

config.autoAddCss = false

const app = createApp(App)

app.use(createPinia())
app.use(router)

// Not waited for: what's public shows in the meantime
void useAuthStore().init()

app.mount('#app')
