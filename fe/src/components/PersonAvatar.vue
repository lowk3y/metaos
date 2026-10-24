<script setup lang="ts">
import { computed } from 'vue'
import { type Image, src, srcset } from '@/images'

const props = defineProps<{
  image: Image | null
  name: string
  // Across, in CSS pixels
  size: number
}>()

// Without a picture, their initials: the first letters of the first and last name, past a "dr."
const initials = computed(() => {
  const words = props.name.replace(/^dr\.\s+/i, '').split(/\s+/).filter(Boolean)
  return ((words[0]?.[0] ?? '') + (words.length > 1 ? words.at(-1)![0] : '')).toUpperCase()
})
</script>

<!-- A person's picture, square and cropped to fill it, or else their initials; beside their name, so it says nothing more -->
<template>
  <img
    v-if="image"
    class="avatar"
    :src="src(image, size * 2)"
    :srcset="srcset(image)"
    :sizes="`${size}px`"
    :width="size"
    :height="size"
    alt=""
    loading="lazy"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
  <span v-else class="avatar initials" aria-hidden="true" :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.38)}px` }">{{ initials }}</span>
</template>

<style scoped lang="scss">
@use '../assets/pictures';

// Round, as people are everywhere (pictures.scss)
.avatar {
  display: block;
  flex: none;
  object-fit: cover;
  background: pictures.$empty;
  border-radius: 50%;
  @include pictures.edge;
}

.initials {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--muted);
  user-select: none;
}
</style>
