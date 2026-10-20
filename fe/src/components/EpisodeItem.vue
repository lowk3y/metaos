<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import ListenLinks from '@/components/ListenLinks.vue'
import MarkdownText from '@/components/MarkdownText.vue'
import ShowBadge from '@/components/ShowBadge.vue'
import { type Episode, episodePath, formatDate, guests } from '@/episodes'
import { useAuthStore } from '@/stores/auth'

// Signed in, the title leads to the episode's page
const auth = useAuthStore()

const props = defineProps<{ episode: Episode }>()

const guestNames = computed(() => guests(props.episode).join(', '))
</script>

<!-- A released episode: the code, then its title, tiny summary, when it aired and its guests, then where to listen.
     The episodes in the works have a row of their own, EpisodeRow. -->
<template>
  <li class="episode">
    <span class="code" :aria-hidden="!episode.code">{{ episode.code ?? '—' }}</span>
    <div class="main">
      <p class="title">
        <RouterLink v-if="auth.authenticated" :to="episodePath(episode)" lang="sl">{{ episode.title }}</RouterLink>
        <span v-else lang="sl">{{ episode.title }}</span>
        <ShowBadge v-if="episode.show" :name="episode.show" :color="episode.showColor"/>
      </p>
      <MarkdownText v-if="episode.tinySummary" class="summary" :source="episode.tinySummary" lang="sl"/>
      <p v-if="episode.airedDate || guestNames" class="details">
        <time v-if="episode.airedDate" :datetime="episode.airedDate">{{ formatDate(episode.airedDate) }}</time>
        <span v-if="guestNames" class="guests">{{ guestNames }}</span>
      </p>
    </div>
    <div v-if="episode.youtubeUrl || episode.spotifyUrl || episode.applePodcastsUrl || episode.patreonUrl" class="links">
      <ListenLinks :episode="episode"/>
    </div>
  </li>
</template>

<style scoped lang="scss">
@use '../assets/episode';

.episode {
  display: grid;
  grid-template-columns: episode.$code-width minmax(0, 1fr) auto;
  padding: 18px 0;
}

@include episode.summary-row;

// On the title's line
.code {
  line-height: 24px;
}

// On phones the code goes above the title, so the text has the whole width
@media (max-width: 640px) {
  .episode {
    grid-template-columns: minmax(0, 1fr);
    padding: 16px 0 12px;
  }

  .code {
    font-size: 12px;
    line-height: 18px;
  }

  .title {
    margin-top: 2px;
  }
}

</style>
