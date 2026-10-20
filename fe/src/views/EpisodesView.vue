<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import EpisodeItem from '@/components/EpisodeItem.vue'
import { type Episode, EPISODE_FIELDS } from '@/episodes'
import { errorMessage, graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// Every episode for signed-in users; for everyone else only the released ones
const episodes = ref<Episode[]>()
const error = ref<string>()

interface Season {
  id: string
  title: string
  episodes: Episode[]
}

// By the season in their codes (S05E08 is in season 5), as they come: the latest first, and ideas, which can do
// without a code, last
const seasons = computed(() => {
  const groups = new Map<string, Season>()
  for (const episode of episodes.value ?? []) {
    const season = episode.code?.match(/^S(\d+)E/i)?.[1]
    const id = season ? `season-${Number(season)}` : 'ideas'
    if (!groups.has(id)) groups.set(id, { id, title: season ? `Season ${Number(season)}` : 'Ideas', episodes: [] })
    groups.get(id)!.episodes.push(episode)
  }
  return [...groups.values()]
})

// Loads again when the user signs in or out, as signed in there's more; only the latest load counts
let loads = 0

watch(() => auth.authenticated, async () => {
  const load = ++loads
  try {
    const data = await graphql<{ episodes: Episode[] }>(`{ episodes { ${EPISODE_FIELDS} } }`)
    if (load !== loads) return
    episodes.value = data.episodes
    error.value = undefined
  } catch (e) {
    if (load === loads) error.value = errorMessage(e)
  }
}, { immediate: true })
</script>

<template>
  <main class="content">
    <p v-if="auth.ready && !auth.authenticated" class="note">
      These are the released episodes. Signed in, you also see the ideas and what's being recorded and produced.
    </p>

    <p v-if="error" class="note">The episodes didn't load: {{ error }}</p>
    <p v-else-if="!episodes" class="note">Loading…</p>
    <section v-for="season in seasons" v-else :key="season.id" :aria-labelledby="season.id">
      <div class="heading">
        <h2 :id="season.id">{{ season.title }}</h2>
        <span class="count">{{ season.episodes.length }}</span>
      </div>
      <!-- A released episode shows when it aired and where to listen; the others their status -->
      <ul class="episodes">
        <EpisodeItem v-for="episode in season.episodes" :key="episode.id" :episode="episode"
                     :released="episode.status === 'PUBLISHED'"/>
      </ul>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '../assets/page';
</style>
