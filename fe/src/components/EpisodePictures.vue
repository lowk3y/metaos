<script setup lang="ts">
import { computed } from 'vue'
import type { EpisodeDetails } from '@/episodes'
import { type Image, src, srcset } from '@/images'

const props = defineProps<{
  episode: EpisodeDetails
}>()

interface Picture {
  kind: 'thumbnail' | 'cover'
  // A thumbnail's letter in the A/B test, with the title it goes with
  variant?: string
  title?: string | null
  image: Image
}

// The thumbnails by the titles they go with, then the cover
const pictures = computed<Picture[]>(() => {
  const { episode } = props
  const all: (Picture | null)[] = [
    episode.thumbnail && { kind: 'thumbnail', variant: 'A', title: episode.title, image: episode.thumbnail },
    episode.thumbnailB && { kind: 'thumbnail', variant: 'B', title: episode.titleB, image: episode.thumbnailB },
    episode.thumbnailC && { kind: 'thumbnail', variant: 'C', title: episode.titleC, image: episode.thumbnailC },
    episode.cover && { kind: 'cover', image: episode.cover },
  ]
  return all.filter((picture): picture is Picture => picture !== null)
})
</script>

<!-- An episode's thumbnails and cover, as everyone sees them from media.ogrodje.si; each opens the original. Only the
     letters say which goes with which title when there's only one thumbnail. -->
<template>
  <section v-if="pictures.length" aria-labelledby="pictures">
    <div class="heading">
      <h2 id="pictures">Pictures</h2>
      <span class="count">{{ pictures.length }}</span>
    </div>
    <ul class="pictures">
      <li v-for="picture in pictures" :key="picture.variant ?? picture.kind" :class="picture.kind">
        <a :href="picture.image.url" target="_blank" rel="noopener" :title="`${picture.image.name}, ${picture.image.width} × ${picture.image.height}`">
          <img
            :src="src(picture.image, 640)"
            :srcset="srcset(picture.image)"
            sizes="(max-width: 640px) calc(100vw - 32px), 280px"
            :width="picture.image.width"
            :height="picture.image.height"
            :alt="picture.kind === 'cover' ? 'Cover' : `Thumbnail ${picture.variant}`"
            loading="lazy"
          >
        </a>
        <p class="caption">
          <template v-if="picture.kind === 'cover'">Cover</template>
          <template v-else><span class="variant">{{ picture.variant }}</span><span lang="sl">{{ picture.title ?? 'Thumbnail' }}</span></template>
        </p>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
@use '../assets/heading';

.pictures {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 24px 16px;
  align-items: start;
  padding: 0;
  list-style: none;
}

a {
  display: block;
  border-radius: 8px;
  overflow: hidden;
  background: var(--hover);
}

img {
  display: block;
  width: 100%;
  height: auto;
  object-fit: cover;
}

.thumbnail img {
  aspect-ratio: 16 / 9;
}

.cover img {
  aspect-ratio: 1;
}

.caption {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--secondary);
}

.variant {
  font-family: var(--font-mono);
  color: var(--muted);
}

@media (max-width: 640px) {
  .pictures {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
