<script setup lang="ts">
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { faKoofr } from '@/brands'
import type { KoofrFolderLink } from '@/episodes'

defineProps<{ folders: KoofrFolderLink[] }>()
</script>

<!-- In Koofr green when pointed at -->
<template>
  <a v-for="folder in folders" :key="folder.url" :href="folder.url" target="_blank" rel="noopener"
     class="icon-link brand-link" style="--brand: #4E8D03" :title="`Koofr: ${folder.path}`"
     :aria-label="`Koofr folder ${folder.path}`">
    <FontAwesomeIcon :icon="faKoofr"/>
  </a>
</template>
