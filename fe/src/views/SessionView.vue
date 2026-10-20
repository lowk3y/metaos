<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DocLinks from '@/components/DocLinks.vue'
import DropboxFolderList from '@/components/DropboxFolderList.vue'
import EpisodeItem from '@/components/EpisodeItem.vue'
import HistoryTimeline from '@/components/HistoryTimeline.vue'
import KoofrFolderList from '@/components/KoofrFolderList.vue'
import PhotoStrip from '@/components/PhotoStrip.vue'
import SignInPrompt from '@/components/SignInPrompt.vue'
import { DROPBOX_FIELDS, EPISODE_FIELDS, formatDate, HISTORY_FIELDS, KOOFR_FIELDS, photosOf, type SessionDetails } from '@/episodes'
import { errorMessage, graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()

// Undefined while it loads; null when there's no such session
const session = ref<SessionDetails | null>()
const error = ref<string>()

// Only the latest load counts: an earlier one may answer after it
let loads = 0

// The session in the path: again when the path changes, or the user signs in
watch([() => route.params.id, () => auth.authenticated], async ([id, authenticated]) => {
  const load = ++loads
  session.value = undefined
  error.value = undefined
  if (!authenticated) return
  try {
    const data = await graphql<{ session: SessionDetails | null }>(`query ($id: ID!) {
      session(id: $id) {
        id date location address documents { fileId url } ${KOOFR_FIELDS} ${DROPBOX_FIELDS} ${HISTORY_FIELDS}
        episodes { ${EPISODE_FIELDS} }
      }
    }`, { id: String(id) })
    if (load === loads) session.value = data.session
  } catch (e) {
    if (load === loads) error.value = errorMessage(e)
  }
}, { immediate: true })
</script>

<template>
  <main class="content">
    <SignInPrompt v-if="auth.ready && !auth.authenticated" title="Sign in to see this session">
      A recording session's details and history are for Ogrodje's team. Sign in with Discord or Google, or use MetaOS
      from your AI assistant over MCP: Usage shows how to connect it.
    </SignInPrompt>
    <p v-else-if="error" class="note">The session didn't load: {{ error }}</p>
    <p v-else-if="session === null" class="note">There's no such session.</p>
    <p v-else-if="!session" class="note">Loading…</p>

    <template v-else>
      <header class="title">
        <p class="kicker">Recording session</p>
        <h1><time :datetime="session.date">{{ formatDate(session.date) }}</time></h1>
        <p v-if="session.location" class="subtitle">{{ session.location }}</p>
      </header>

      <section v-if="session.address || session.documents.length || session.koofrFolders.length || session.dropboxFolders.length"
               aria-labelledby="details">
        <div class="heading"><h2 id="details">Details</h2></div>
        <dl class="details">
          <div v-if="session.address">
            <dt>Address</dt>
            <dd>{{ session.address }}</dd>
          </div>
          <div v-if="session.documents.length">
            <dt>Docs</dt>
            <dd class="icons"><DocLinks :documents="session.documents"/></dd>
          </div>
          <div v-if="session.koofrFolders.length">
            <dt>Koofr</dt>
            <dd><KoofrFolderList :folders="session.koofrFolders"/></dd>
          </div>
          <div v-if="session.dropboxFolders.length">
            <dt>Dropbox</dt>
            <dd><DropboxFolderList :folders="session.dropboxFolders"/></dd>
          </div>
        </dl>
      </section>

      <section v-if="photosOf(session.koofrFolders).length" aria-labelledby="photos">
        <div class="heading"><h2 id="photos">Photos</h2></div>
        <PhotoStrip :photos="photosOf(session.koofrFolders)" :folder="session.koofrFolders[0]?.url"/>
      </section>

      <section aria-labelledby="episodes">
        <div class="heading">
          <h2 id="episodes">Episodes</h2>
          <span class="count">{{ session.episodes.length }}</span>
        </div>
        <p v-if="!session.episodes.length" class="note">No episodes are in this session yet.</p>
        <ul v-else class="episodes">
          <EpisodeItem v-for="episode in session.episodes" :key="episode.id" :episode="episode"/>
        </ul>
      </section>

      <section aria-labelledby="history">
        <div class="heading">
          <h2 id="history">History</h2>
          <span class="count">{{ session.history.length }}</span>
        </div>
        <HistoryTimeline :changes="session.history"/>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">
@use '../assets/page';
</style>
