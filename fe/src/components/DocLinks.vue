<script setup lang="ts">
import { faFileLines } from '@fortawesome/free-regular-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import type { GoogleDoc } from '@/episodes'

defineProps<{ documents: GoogleDoc[] }>()
</script>

<template>
  <a v-for="(doc, index) in documents" :key="doc.fileId" :href="doc.url" target="_blank" rel="noopener"
     class="icon-link doc" :title="documents.length > 1 ? `Google Doc ${index + 1}` : 'Google Doc'"
     :aria-label="documents.length > 1 ? `Google Doc ${index + 1}` : 'Google Doc'">
    <FontAwesomeIcon :icon="faFileLines"/>
  </a>
</template>

<style scoped lang="scss">

// Google Docs blue
.doc {
  color: #4285F4;
}

</style>
