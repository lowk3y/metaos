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
    // Every episode by season, but the ideas; for anyone not signed in, the released ones
    {
      path: '/episodes',
      name: 'episodes',
      component: () => import('../views/EpisodesView.vue'),
    },
    // Everyone, with where they're online; for anyone not signed in, those on the released episodes
    {
      path: '/people',
      name: 'people',
      component: () => import('../views/PeopleView.vue'),
    },
    // A person's page, with every episode they're on, behind sign-in
    {
      path: '/people/:id',
      name: 'person',
      component: () => import('../views/PersonView.vue'),
    },
    // Everything a search finds, behind sign-in: where the header's search leads for all of it
    {
      path: '/search',
      name: 'search',
      component: () => import('../views/SearchView.vue'),
    },
    // The episode ideas, behind sign-in
    {
      path: '/ideas',
      name: 'ideas',
      component: () => import('../views/IdeasView.vue'),
    },
    // An episode's and a session's page, behind sign-in; an episode by its code, or its ID while it has none
    {
      path: '/episodes/:ref',
      name: 'episode',
      component: () => import('../views/EpisodeView.vue'),
    },
    {
      path: '/sessions/:id',
      name: 'session',
      component: () => import('../views/SessionView.vue'),
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
