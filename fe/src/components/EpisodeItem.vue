<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import DocLinks from '@/components/DocLinks.vue'
import DropboxLinks from '@/components/DropboxLinks.vue'
import KoofrLinks from '@/components/KoofrLinks.vue'
import ShowBadge from '@/components/ShowBadge.vue'
import { brands } from '@/brands'
import { type Episode, episodePath, formatDate, links, roleName, statusName } from '@/episodes'
import { useAuthStore } from '@/stores/auth'

// Signed in, the title leads to the episode's page
const auth = useAuthStore()

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
      <p class="title" lang="sl">
        <RouterLink v-if="auth.authenticated" :to="episodePath(episode)">{{ episode.title }}</RouterLink>
        <template v-else>{{ episode.title }}</template>
      </p>
      <p v-if="date || episode.people.length" class="details">
        <time v-if="date" :datetime="date">{{ formatDate(date) }}</time>
        <span v-if="date && episode.people.length" aria-hidden="true"> · </span>
        <!-- Each on one line: a line break before the comma would show as a space -->
        <span v-for="(person, index) in episode.people" :key="person.personId"><template v-if="index">, </template><span class="person">{{ person.name }}</span> ({{ person.roles.map(roleName).join(', ') }})</span>
      </p>
    </div>
    <div class="side">
      <ShowBadge v-if="episode.show" :name="episode.show" :color="episode.showColor"/>
      <span v-if="!released" class="status">{{ statusName(episode.status) }}</span>
      <a v-for="link in links(episode)" :key="link.platform" class="icon-link" :href="link.url" :title="link.platform"
         :aria-label="link.platform" :style="{ color: brands[link.platform].color }">
        <FontAwesomeIcon :icon="brands[link.platform].icon"/>
      </a>
      <DocLinks :documents="episode.documents"/>
      <KoofrLinks :folders="episode.koofrFolders"/>
      <DropboxLinks :folders="episode.dropboxFolders"/>
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

  // Apart from the show's badge, as they're about different things
  &:not(:first-child) {
    margin-left: 2px;
  }
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
