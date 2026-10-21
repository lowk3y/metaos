<script setup lang="ts">
import { computed } from 'vue'
import { markWords } from '@/search'

// A text with the words a search found in it marked; as it is without words
const props = defineProps<{ text: string, words?: string[] }>()

const parts = computed(() => (props.words?.length ? markWords(props.text, props.words) : [{ text: props.text, marked: false }]))
</script>

<template>
  <template v-for="(part, i) in parts" :key="i"><mark v-if="part.marked">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template>
</template>
