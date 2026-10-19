import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/usage',
      name: 'usage',
      // Its own chunk, loaded when the page is first visited
      component: () => import('../views/UsageView.vue'),
    },
  ],
})

export default router
