<script setup lang="ts">
import DocLinks from '@/components/DocLinks.vue'
import DropboxLinks from '@/components/DropboxLinks.vue'
import KoofrLinks from '@/components/KoofrLinks.vue'
import type { Episode } from '@/episodes'

// An episode's or a session's files: its Google Docs, then its Koofr and Dropbox folders
defineProps<{ of: Pick<Episode, 'documents' | 'koofrFolders' | 'dropboxFolders'> }>()
</script>

<template>
  <DocLinks :documents="of.documents"/>
  <KoofrLinks :folders="of.koofrFolders"/>
  <DropboxLinks :folders="of.dropboxFolders"/>
</template>
