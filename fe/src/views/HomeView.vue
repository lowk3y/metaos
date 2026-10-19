<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { faDiscord, faGoogle } from '@fortawesome/free-brands-svg-icons'
import { faArrowRight } from '@fortawesome/free-solid-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import DocLinks from '@/components/DocLinks.vue'
import EpisodeItem from '@/components/EpisodeItem.vue'
import { bySession, type Episode, EPISODE_FIELDS, formatDate, type SessionGroup } from '@/episodes'
import { graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const released = ref<Episode[]>()
const releasedError = ref<string>()

// What's being made, by recording session - for signed-in users only
const upcoming = ref<SessionGroup[]>()
const inProduction = ref<SessionGroup[]>()
const makingError = ref<string>()

interface Section {
  id: string
  title: string
  groups: SessionGroup[]
  count: number
}

// Only those with episodes in them, once they've loaded
const sections = computed(() =>
  [
    { id: 'in-production', title: 'In production', groups: inProduction.value },
    { id: 'upcoming-recordings', title: 'Upcoming recordings', groups: upcoming.value },
  ].flatMap(({ id, title, groups }): Section[] =>
    groups?.length ? [{ id, title, groups, count: groups.reduce((n, group) => n + group.episodes.length, 0) }] : []),
)

function message(error: unknown) {
  return error instanceof Error ? error.message : String(error)
}

// The lists load again when the user signs in or out, as signed in they have more in them, e.g. docs. Only the
// latest load counts: an earlier one may answer after it.
let loads = 0

async function loadReleased(load: number) {
  try {
    const data = await graphql<{ releasedEpisodes: Episode[] }>(`{
      releasedEpisodes(limit: 3) { ${EPISODE_FIELDS} }
    }`)
    if (load !== loads) return
    released.value = data.releasedEpisodes
    releasedError.value = undefined
  } catch (error) {
    if (load === loads) releasedError.value = message(error)
  }
}

async function loadMaking(load: number) {
  try {
    const data = await graphql<{ upcomingRecordings: Episode[], inProduction: Episode[] }>(`{
      upcomingRecordings { ${EPISODE_FIELDS} }
      inProduction { ${EPISODE_FIELDS} }
    }`)
    if (load !== loads) return
    upcoming.value = bySession(data.upcomingRecordings)
    inProduction.value = bySession(data.inProduction)
  } catch (error) {
    if (load === loads) makingError.value = message(error)
  }
}

watch(() => auth.authenticated, (authenticated) => {
  const load = ++loads
  upcoming.value = undefined
  inProduction.value = undefined
  makingError.value = undefined
  void loadReleased(load)
  if (authenticated) void loadMaking(load)
}, { immediate: true })
</script>

<template>
  <main>
    <!-- For visitors who aren't signed in: how to get at the rest -->
    <section v-if="auth.ready && !auth.authenticated" class="welcome" aria-labelledby="sign-in">
      <h2 id="sign-in">Sign in to use MetaOS</h2>
      <p>
        Sign in with Discord or Google to see the episodes in production and the upcoming recordings. Or use MetaOS
        from your AI assistant over MCP: Usage shows how to connect it.
      </p>
      <div class="actions">
        <button type="button" class="button" @click="auth.login('discord')">
          <FontAwesomeIcon :icon="faDiscord"/>Sign in with Discord
        </button>
        <button type="button" class="button" @click="auth.login('google')">
          <FontAwesomeIcon :icon="faGoogle"/>Sign in with Google
        </button>
        <RouterLink class="button" to="/usage">Usage with MCP<FontAwesomeIcon :icon="faArrowRight"/></RouterLink>
      </div>
      <p class="approval">New accounts get in once an admin has approved them.</p>
    </section>

    <section aria-labelledby="latest-releases">
      <div class="heading">
        <h2 id="latest-releases">Latest releases</h2>
        <span v-if="released" class="count">{{ released.length }}</span>
      </div>
      <p v-if="releasedError" class="note">The episodes didn't load: {{ releasedError }}</p>
      <p v-else-if="!released" class="note">Loading…</p>
      <ul v-else class="episodes">
        <EpisodeItem v-for="episode in released" :key="episode.id" :episode="episode" released/>
      </ul>
    </section>

    <template v-if="auth.authenticated">
      <p v-if="makingError" class="note">The upcoming recordings and what's in production didn't load: {{ makingError }}</p>
      <section v-for="section in sections" v-else :key="section.id" :aria-labelledby="section.id">
        <div class="heading">
          <h2 :id="section.id">{{ section.title }}</h2>
          <span class="count">{{ section.count }}</span>
        </div>
        <!-- Episodes without a session are listed as they are, without a heading -->
        <div v-for="group in section.groups" :key="group.key" class="group">
          <h3 v-if="group.session" class="session">
            <time :datetime="group.session.date">{{ formatDate(group.session.date) }}</time>
            <span v-if="group.session.location" class="location">{{ group.session.location }}</span>
            <DocLinks :documents="group.session.documents"/>
          </h3>
          <ul class="episodes">
            <EpisodeItem v-for="episode in group.episodes" :key="episode.id" :episode="episode"/>
          </ul>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">

main {
  max-width: var(--content-width);
  margin: 0 auto;
  padding: 48px 24px 96px;
  display: flex;
  flex-direction: column;
  gap: 56px;
}

.welcome {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px;
  border: 1px solid var(--hairline);
  border-radius: 12px;

  h2 {
    font-size: 15px;
    line-height: 24px;
    font-weight: 600;
  }

  p {
    max-width: 640px;
    color: var(--secondary);
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
  }

  .approval {
    font-size: 13px;
    line-height: 20px;
    color: var(--muted);
  }
}

.heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-bottom: 12px;

  h2 {
    font-size: 15px;
    line-height: 24px;
    font-weight: 600;
  }
}

.count, .note {
  font-size: 13px;
  line-height: 20px;
  color: var(--muted);
}

.episodes {
  padding: 0;
  list-style: none;
}

.session {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 20px 0 8px;
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;

  .location {
    font-weight: 400;
    color: var(--secondary);
  }
}

// Right under the section's heading, the heading's own space is enough
.heading + .group .session {
  padding-top: 8px;
}

</style>
