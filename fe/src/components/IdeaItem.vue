<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import FileLinks from '@/components/FileLinks.vue'
import MarkdownText from '@/components/MarkdownText.vue'
import ShowBadge from '@/components/ShowBadge.vue'
import { candidates, episodePath, formatDate, guests, type Idea, sessionPath } from '@/episodes'

const props = defineProps<{ idea: Idea }>()

const guestNames = computed(() => guests(props.idea).join(', '))
const candidateNames = computed(() => candidates(props.idea).join(', '))

// When it's to be recorded, if that's known: its own date, or else its session's
const recording = computed(() => props.idea.recordingDate ?? props.idea.session?.date ?? null)

const hasFiles = computed(() =>
  props.idea.documents.length + props.idea.koofrFolders.length + props.idea.dropboxFolders.length > 0)
</script>

<!-- An idea: its title and tiny summary, then when it was added and who recommended it, when it's to be recorded, its
     guests and those who could be, then its files. Like a released episode's row (EpisodeItem), without the code's column, as ideas mostly
     have none. -->
<template>
  <li class="episode">
    <div class="main">
      <p class="title">
        <RouterLink :to="episodePath(idea)" lang="sl">{{ idea.title }}</RouterLink>
        <ShowBadge v-if="idea.show" :name="idea.show" :color="idea.showColor"/>
      </p>
      <MarkdownText v-if="idea.tinySummary" class="summary" :source="idea.tinySummary" lang="sl"/>
      <p class="details">
        <span v-if="idea.code" class="code">{{ idea.code }}</span>
        <span class="when">Added <time :datetime="idea.addedDate">{{ formatDate(idea.addedDate) }}</time></span>
        <span v-if="idea.recommendedBy">Recommended by <span class="person">{{ idea.recommendedBy.name }}</span></span>
        <!-- On one line, as a line break would show as a space before the separator -->
        <span v-if="recording" class="when">Recording <RouterLink v-if="idea.session" :to="sessionPath(idea.session)"><time :datetime="recording">{{ formatDate(recording) }}</time></RouterLink><time v-else :datetime="recording">{{ formatDate(recording) }}</time></span>
        <span v-if="guestNames" class="guests">{{ guestNames }}</span>
        <span v-if="candidateNames">Maybe <span class="person">{{ candidateNames }}</span></span>
      </p>
    </div>
    <div v-if="hasFiles" class="links"><FileLinks :of="idea"/></div>
  </li>
</template>

<style scoped lang="scss">
@use '../assets/episode';

.episode {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 18px 0;
}

@include episode.summary-row;

// Among the details, in their size
.code {
  font-size: 12px;
  line-height: 20px;
}

.details a {
  color: var(--secondary);
}

@media (max-width: 640px) {
  .episode {
    grid-template-columns: minmax(0, 1fr);
    padding: 16px 0 12px;
  }
}

</style>
