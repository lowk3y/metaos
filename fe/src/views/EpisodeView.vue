<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import DocLinks from '@/components/DocLinks.vue'
import HistoryTimeline from '@/components/HistoryTimeline.vue'
import KoofrFolderList from '@/components/KoofrFolderList.vue'
import MarkdownText from '@/components/MarkdownText.vue'
import PhotoStrip from '@/components/PhotoStrip.vue'
import SignInPrompt from '@/components/SignInPrompt.vue'
import { brands } from '@/brands'
import {
  EPISODE_FIELDS, type EpisodeDetails, formatDate, HISTORY_FIELDS, KOOFR_FIELDS, links, photosOf, roleName,
  sessionPath, sessionTitle, statusName,
} from '@/episodes'
import { errorMessage, graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()

// Undefined while it loads; null when there's no such episode
const episode = ref<EpisodeDetails | null>()
const error = ref<string>()

// Only the latest load counts: an earlier one may answer after it
let loads = 0

// The episode in the path, by its code or ID: again when the path changes, or the user signs in
watch([() => route.params.ref, () => auth.authenticated], async ([episodeRef, authenticated]) => {
  const load = ++loads
  episode.value = undefined
  error.value = undefined
  if (!authenticated) return
  try {
    const data = await graphql<{ episode: EpisodeDetails | null }>(`query ($ref: String!) {
      episode(ref: $ref) { ${EPISODE_FIELDS} show summary machineSummary addedDate ${KOOFR_FIELDS} ${HISTORY_FIELDS} }
    }`, { ref: String(episodeRef) })
    if (load === loads) episode.value = data.episode
  } catch (e) {
    if (load === loads) error.value = errorMessage(e)
  }
}, { immediate: true })
</script>

<template>
  <main class="content">
    <SignInPrompt v-if="auth.ready && !auth.authenticated" title="Sign in to see this episode">
      An episode's details and history are for Ogrodje's team. Sign in with Discord or Google, or use MetaOS from your
      AI assistant over MCP: Usage shows how to connect it.
    </SignInPrompt>
    <p v-else-if="error" class="note">The episode didn't load: {{ error }}</p>
    <p v-else-if="episode === null" class="note">There's no episode {{ route.params.ref }}.</p>
    <p v-else-if="!episode" class="note">Loading…</p>

    <template v-else>
      <header class="title">
        <p class="kicker">
          <span v-if="episode.code" class="code">{{ episode.code }}</span>
          <span class="status">{{ statusName(episode.status) }}</span>
        </p>
        <h1 lang="sl">{{ episode.title }}</h1>
        <p v-if="episode.show" class="subtitle">{{ episode.show }}</p>
      </header>

      <section aria-labelledby="details">
        <div class="heading"><h2 id="details">Details</h2></div>
        <dl class="details">
          <div v-if="episode.session">
            <dt>Session</dt>
            <dd><RouterLink :to="sessionPath(episode.session)">{{ sessionTitle(episode.session) }}</RouterLink></dd>
          </div>
          <div v-if="episode.recordingDate">
            <dt>Recording</dt>
            <dd><time :datetime="episode.recordingDate">{{ formatDate(episode.recordingDate) }}</time></dd>
          </div>
          <div v-if="episode.airedDate">
            <dt>Aired</dt>
            <dd><time :datetime="episode.airedDate">{{ formatDate(episode.airedDate) }}</time></dd>
          </div>
          <div>
            <dt>Added</dt>
            <dd><time :datetime="episode.addedDate">{{ formatDate(episode.addedDate) }}</time></dd>
          </div>
          <div v-if="links(episode).length">
            <dt>Listen</dt>
            <dd class="icons">
              <a v-for="link in links(episode)" :key="link.platform" class="icon-link" :href="link.url"
                 :title="link.platform" :aria-label="link.platform" :style="{ color: brands[link.platform].color }">
                <FontAwesomeIcon :icon="brands[link.platform].icon"/>
              </a>
            </dd>
          </div>
          <div v-if="episode.documents.length">
            <dt>Docs</dt>
            <dd class="icons"><DocLinks :documents="episode.documents"/></dd>
          </div>
          <div v-if="episode.koofrFolders.length">
            <dt>Koofr</dt>
            <dd><KoofrFolderList :folders="episode.koofrFolders"/></dd>
          </div>
        </dl>
      </section>

      <section v-if="photosOf(episode.koofrFolders).length" aria-labelledby="photos">
        <div class="heading"><h2 id="photos">Photos</h2></div>
        <PhotoStrip :photos="photosOf(episode.koofrFolders)" :folder="episode.koofrFolders[0]?.url"/>
      </section>

      <section v-if="episode.people.length" aria-labelledby="people">
        <div class="heading">
          <h2 id="people">People</h2>
          <span class="count">{{ episode.people.length }}</span>
        </div>
        <ul class="rows">
          <li v-for="person in episode.people" :key="person.personId">
            <span>{{ person.name }}</span>
            <span class="aside">{{ person.roles.map(roleName).join(', ') }}</span>
          </li>
        </ul>
      </section>

      <section v-if="episode.summary" aria-labelledby="summary">
        <div class="heading"><h2 id="summary">Summary</h2></div>
        <MarkdownText :source="episode.summary" lang="sl"/>
      </section>

      <section v-if="episode.machineSummary" aria-labelledby="ai-summary">
        <div class="heading"><h2 id="ai-summary">AI summary</h2></div>
        <MarkdownText :source="episode.machineSummary" lang="sl"/>
      </section>

      <section aria-labelledby="history">
        <div class="heading">
          <h2 id="history">History</h2>
          <span class="count">{{ episode.history.length }}</span>
        </div>
        <HistoryTimeline :changes="episode.history"/>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">
@use '../assets/page';
</style>
