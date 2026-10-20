<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import DocLinks from '@/components/DocLinks.vue'
import DropboxFolderList from '@/components/DropboxFolderList.vue'
import HistoryTimeline from '@/components/HistoryTimeline.vue'
import KoofrFolderList from '@/components/KoofrFolderList.vue'
import ListenLinks from '@/components/ListenLinks.vue'
import MarkdownText from '@/components/MarkdownText.vue'
import PhotoStrip from '@/components/PhotoStrip.vue'
import ShowBadge from '@/components/ShowBadge.vue'
import SignInPrompt from '@/components/SignInPrompt.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import {
  ARTWORK_FIELDS, artworkOf, DROPBOX_FIELDS, EPISODE_FIELDS, type EpisodeDetails, formatDate, HISTORY_FIELDS, KOOFR_FIELDS,
  links, photosOf, roleName, sessionPath, sessionTitle,
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
      episode(ref: $ref) {
        ${EPISODE_FIELDS} titleB titleC summary machineSummary addedDate recommendedBy { name } ${KOOFR_FIELDS} ${DROPBOX_FIELDS} ${ARTWORK_FIELDS}
        ${HISTORY_FIELDS}
      }
    }`, { ref: String(episodeRef) })
    if (load === loads) episode.value = data.episode
  } catch (e) {
    if (load === loads) error.value = errorMessage(e)
  }
}, { immediate: true })

// The pictures in its Dropbox folders, which open in the folder of the first; and its photos, in its first Koofr folder
const artwork = computed(() => (episode.value ? artworkOf(episode.value.dropboxFolders) : []))
const photos = computed(() => (episode.value ? photosOf(episode.value.koofrFolders) : []))
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
          <ShowBadge v-if="episode.show" :name="episode.show" :color="episode.showColor"/>
          <StatusLabel :status="episode.status"/>
        </p>
        <h1 lang="sl">{{ episode.title }}</h1>
        <MarkdownText v-if="episode.tinySummary" :source="episode.tinySummary" lang="sl"/>
      </header>

      <section aria-labelledby="details">
        <div class="heading"><h2 id="details">Details</h2></div>
        <dl class="details">
          <div v-if="episode.titleB || episode.titleC">
            <dt>Alternative titles</dt>
            <dd>
              <ul class="alternatives" lang="sl">
                <li v-if="episode.titleB"><span class="variant">B</span>{{ episode.titleB }}</li>
                <li v-if="episode.titleC"><span class="variant">C</span>{{ episode.titleC }}</li>
              </ul>
            </dd>
          </div>
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
          <div v-if="episode.recommendedBy">
            <dt>Recommended by</dt>
            <dd>{{ episode.recommendedBy.name }}</dd>
          </div>
          <div v-if="links(episode).length">
            <dt>Listen</dt>
            <dd class="icons"><ListenLinks :episode="episode"/></dd>
          </div>
          <div v-if="episode.documents.length">
            <dt>Docs</dt>
            <dd class="icons"><DocLinks :documents="episode.documents"/></dd>
          </div>
          <div v-if="episode.koofrFolders.length">
            <dt>Koofr</dt>
            <dd><KoofrFolderList :folders="episode.koofrFolders"/></dd>
          </div>
          <div v-if="episode.dropboxFolders.length">
            <dt>Dropbox</dt>
            <dd><DropboxFolderList :folders="episode.dropboxFolders"/></dd>
          </div>
        </dl>
      </section>

      <PhotoStrip v-if="artwork.length" title="Artwork" :photos="artwork" :folder="artwork[0]?.url" service="Dropbox" artwork/>
      <PhotoStrip v-if="photos.length" title="Photos" :photos="photos" :folder="episode.koofrFolders[0]?.url" service="Koofr"/>

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

// Each after its letter in the A/B test; a long one wraps beside it
.alternatives {
  padding: 0;
  list-style: none;

  li {
    display: grid;
    grid-template-columns: 20px minmax(0, 1fr);
    align-items: baseline;
  }

  li + li {
    margin-top: 4px;
  }
}

.variant {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--muted);
}
</style>
