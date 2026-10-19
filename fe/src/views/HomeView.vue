<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { graphql } from '@/graphql'

interface Episode {
  id: string
  code: string | null
  title: string
  airedDate: string | null
  youtubeUrl: string | null
  spotifyUrl: string | null
  applePodcastsUrl: string | null
  patreonUrl: string | null
}

const platforms = {
  YouTube: 'youtubeUrl',
  Spotify: 'spotifyUrl',
  'Apple Podcasts': 'applePodcastsUrl',
  Patreon: 'patreonUrl',
} as const

const episodes = ref<Episode[]>()
const error = ref<string>()

function links(episode: Episode) {
  return Object.entries(platforms).flatMap(([platform, field]) => {
    const url = episode[field]
    return url ? [{ platform, url }] : []
  })
}

onMounted(async () => {
  try {
    const data = await graphql<{ releasedEpisodes: Episode[] }>(`{
      releasedEpisodes(limit: 3) { id code title airedDate youtubeUrl spotifyUrl applePodcastsUrl patreonUrl }
    }`)
    episodes.value = data.releasedEpisodes
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <main>
    <h1>Hello</h1>
    <p>This is Ogrodje MetaOS. System for managing and operating Ogrodje platform.</p>
    <p>Visit out Discord channel to learn more.</p>

    <h2>Latest episodes</h2>
    <p v-if="error">The episodes didn't load: {{ error }}</p>
    <p v-else-if="!episodes">Loading…</p>
    <ul v-else class="episodes">
      <li v-for="episode in episodes" :key="episode.id">
        <strong>{{ episode.code }}</strong> {{ episode.title }}
        <time v-if="episode.airedDate" :datetime="episode.airedDate">
          {{ new Date(episode.airedDate).toLocaleDateString(undefined, { dateStyle: 'medium' }) }}
        </time>
        <span class="links">
          <a v-for="link in links(episode)" :key="link.platform" :href="link.url">{{ link.platform }}</a>
        </span>
      </li>
    </ul>
  </main>
</template>

<style scoped lang="scss">

.episodes {
  li {
    margin-bottom: 8px;
  }

  time, .links a {
    margin-left: 8px;
  }
}

</style>
