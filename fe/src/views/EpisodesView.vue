<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import EpisodeItem from '@/components/EpisodeItem.vue'
import EpisodeRow from '@/components/EpisodeRow.vue'
import { type Episode, EPISODE_FIELDS } from '@/episodes'
import { errorMessage, graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// Every episode for signed-in users; for everyone else only the released ones
const episodes = ref<Episode[]>()
const error = ref<string>()

// A season's episodes in the works, a line each, and those released, with their summaries and where to listen
interface Season {
  id: string
  title: string
  working: Episode[]
  released: Episode[]
}

// By the season in their codes (S05E08 is in season 5), as they come: the latest first, then the bonus season (S99),
// and those without a code, put on hold or cancelled before they had one, last. Ideas have a page of their own.
const seasons = computed(() => {
  const groups = new Map<string, Season>()
  for (const episode of episodes.value ?? []) {
    const season = episode.code?.match(/^S(\d+)E/i)?.[1]
    const id = season ? `season-${Number(season)}` : 'without-code'
    if (!groups.has(id)) groups.set(id, { id, title: season ? `Season ${Number(season)}` : 'Without a code', working: [], released: [] })
    const group = groups.get(id)!
    ;(episode.status === 'PUBLISHED' ? group.released : group.working).push(episode)
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
      These are the released episodes. Signed in, you also see what's being recorded and produced.
    </p>

    <p v-if="error" class="note">The episodes didn't load: {{ error }}</p>
    <p v-else-if="!episodes" class="note">Loading…</p>
    <section v-for="season in seasons" v-else :key="season.id" :aria-labelledby="season.id">
      <div class="heading">
        <h2 :id="season.id">{{ season.title }}</h2>
        <span class="count">{{ season.working.length + season.released.length }}</span>
      </div>
      <!-- Headed only when a season has both -->
      <template v-if="season.working.length">
        <h3 v-if="season.released.length" class="subheading">
          In the works <span class="count">{{ season.working.length }}</span>
        </h3>
        <ul class="episodes">
          <EpisodeRow v-for="episode in season.working" :key="episode.id" :episode="episode"/>
        </ul>
      </template>
      <template v-if="season.released.length">
        <h3 v-if="season.working.length" class="subheading">
          Released <span class="count">{{ season.released.length }}</span>
        </h3>
        <ul class="episodes">
          <EpisodeItem v-for="episode in season.released" :key="episode.id" :episode="episode"/>
        </ul>
      </template>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '../assets/page';
</style>
