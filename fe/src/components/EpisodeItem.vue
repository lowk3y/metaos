<script setup lang="ts">
import { computed } from 'vue'
import type { IconDefinition } from '@fortawesome/fontawesome-svg-core'
import { faPatreon, faSpotify, faYoutube } from '@fortawesome/free-brands-svg-icons'
import { faPodcast } from '@fortawesome/free-solid-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import DocLinks from '@/components/DocLinks.vue'
import { type Episode, formatDate, links, type Platform, roleName, statusName } from '@/episodes'

// In their brand colours. Font Awesome has no Apple Podcasts logo; its podcast icon looks much like it.
const brands: Record<Platform, { icon: IconDefinition, color: string }> = {
  YouTube: { icon: faYoutube, color: '#FF0000' },
  Spotify: { icon: faSpotify, color: '#1DB954' },
  'Apple Podcasts': { icon: faPodcast, color: '#9933CC' },
  Patreon: { icon: faPatreon, color: '#FF424D' },
}

const props = defineProps<{
  episode: Episode
  // A released episode shows when it aired; the others their status, and when they're recorded unless their
  // session says so already
  released?: boolean
}>()

const date = computed(() => {
  const { airedDate, recordingDate, session } = props.episode
  if (props.released) return airedDate
  return recordingDate !== session?.date ? recordingDate : null
})
</script>

<!-- One row: the code, then the title with its date and people beneath, then its status and links -->
<template>
  <li class="episode">
    <span class="code" :aria-hidden="!episode.code">{{ episode.code ?? '—' }}</span>
    <div class="main">
      <p class="title" lang="sl">{{ episode.title }}</p>
      <p v-if="date || episode.people.length" class="details">
        <time v-if="date" :datetime="date">{{ formatDate(date) }}</time>
        <span v-if="date && episode.people.length" aria-hidden="true"> · </span>
        <!-- Each on one line: a line break before the comma would show as a space -->
        <span v-for="(person, index) in episode.people" :key="person.personId"><template v-if="index">, </template><span class="person">{{ person.name }}</span> ({{ person.roles.map(roleName).join(', ') }})</span>
      </p>
    </div>
    <div class="side">
      <span v-if="!released" class="status">{{ statusName(episode.status) }}</span>
      <a v-for="link in links(episode)" :key="link.platform" class="icon-link" :href="link.url" :title="link.platform"
         :aria-label="link.platform" :style="{ color: brands[link.platform].color }">
        <FontAwesomeIcon :icon="brands[link.platform].icon"/>
      </a>
      <DocLinks :documents="episode.documents"/>
    </div>
  </li>
</template>

<style scoped lang="scss">

.episode {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  column-gap: 16px;
  padding: 14px 0;
  border-top: 1px solid var(--rule);
}

.code {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 22px;
  color: var(--muted);
}

.title {
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
}

.details {
  margin-top: 4px;
  font-size: 13px;
  line-height: 20px;
  color: var(--muted);
}

.person {
  color: var(--secondary);
}

// As tall as the title's line, which the 32 px icon links centre on
.side {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  height: 22px;
}

.status {
  padding: 2px 8px;
  font-size: 12px;
  line-height: 16px;
  font-weight: 500;
  color: var(--secondary);
  white-space: nowrap;
  border: 1px solid var(--hairline);
  border-radius: 999px;
}

// On phones the status and links go under the title
@media (max-width: 640px) {
  .episode {
    grid-template-columns: 60px minmax(0, 1fr);
  }

  .side {
    grid-column: 2;
    justify-content: flex-start;
    height: auto;
    margin-top: 6px;

    // So the first icon, not its hit area, lines up with the text
    > a:first-child {
      margin-left: -8px;
    }
  }
}

</style>
