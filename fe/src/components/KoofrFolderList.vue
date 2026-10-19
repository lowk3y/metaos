<script setup lang="ts">
import { faFolder } from '@fortawesome/free-regular-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import type { KoofrFolder } from '@/episodes'

// Not links: MetaOS knows the folders as the bot sees them, which isn't how they're found in your own Koofr
defineProps<{ folders: KoofrFolder[] }>()
</script>

<template>
  <ul class="folders">
    <li v-for="folder in folders" :key="`${folder.place}${folder.path}`">
      <FontAwesomeIcon :icon="faFolder" class="icon"/>
      <span v-if="folder.placeName">{{ folder.placeName }}</span>
      <span class="path">{{ folder.path }}</span>
    </li>
  </ul>
</template>

<style scoped lang="scss">

.folders {
  padding: 0;
  list-style: none;

  li {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    column-gap: 8px;
  }

  .icon {
    color: var(--muted);
  }

  .path {
    font-family: var(--font-mono);
    font-size: 13px;
    color: var(--secondary);
  }
}

</style>
