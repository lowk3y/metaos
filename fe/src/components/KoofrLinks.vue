<script setup lang="ts">
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { faKoofr } from '@/brands'
import type { KoofrFolderLink } from '@/episodes'

defineProps<{ folders: KoofrFolderLink[] }>()
</script>

<template>
  <a v-for="folder in folders" :key="folder.url" :href="folder.url" target="_blank" rel="noopener"
     class="icon-link koofr" :title="`Koofr: ${folder.path}`" :aria-label="`Koofr folder ${folder.path}`">
    <FontAwesomeIcon :icon="faKoofr"/>
  </a>
</template>

<style scoped lang="scss">

// Koofr green
.koofr {
  color: #4E8D03;
}

</style>
