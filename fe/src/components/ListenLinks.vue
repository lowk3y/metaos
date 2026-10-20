<script setup lang="ts">
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { brands } from '@/brands'
import { type Episode, links } from '@/episodes'

// Where to listen to an episode, each in its brand's colour when pointed at
defineProps<{ episode: Pick<Episode, 'youtubeUrl' | 'spotifyUrl' | 'applePodcastsUrl' | 'patreonUrl'> }>()
</script>

<template>
  <a v-for="link in links(episode)" :key="link.platform" class="icon-link brand-link" :href="link.url"
     :title="link.platform" :aria-label="link.platform" :style="{ '--brand': brands[link.platform].color }">
    <FontAwesomeIcon :icon="brands[link.platform].icon"/>
  </a>
</template>
