<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import type { KoofrPhoto } from '@/episodes'
import { fetchObjectUrl } from '@/graphql'

// After the photos, a tile that opens [folder] in Koofr, for the rest
const props = defineProps<{ photos: KoofrPhoto[], folder?: string }>()

// An <img> can't send the sign-in token, so each thumbnail is fetched with it and shown from memory, by photo path
const sources = ref<Record<string, string>>({})

function release() {
  Object.values(sources.value).forEach((url) => URL.revokeObjectURL(url))
  sources.value = {}
}

watch(() => props.photos, (photos) => {
  release()
  for (const photo of photos) {
    fetchObjectUrl(photo.thumbnail).then((url) => {
      // Photos that changed meanwhile don't need it any more
      if (props.photos.includes(photo)) sources.value = { ...sources.value, [photo.path]: url }
      else URL.revokeObjectURL(url)
    }).catch((error) => console.error(`No thumbnail of ${photo.path}`, error))
  }
}, { immediate: true })

onBeforeUnmount(release)
</script>

<!-- A grid of thumbnails, each opening its folder in Koofr -->
<template>
  <ul class="photos">
    <li v-for="photo in photos" :key="photo.path">
      <a :href="photo.url" target="_blank" rel="noopener" :title="photo.name">
        <img v-if="sources[photo.path]" :src="sources[photo.path]" :alt="photo.name">
      </a>
    </li>
    <li v-if="folder">
      <a class="more" :href="folder" target="_blank" rel="noopener">
        Open on Koofr <FontAwesomeIcon :icon="faArrowUpRightFromSquare"/>
      </a>
    </li>
  </ul>
</template>

<style scoped lang="scss">

.photos {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 0;
  list-style: none;

  a {
    display: block;
    aspect-ratio: 3 / 2;
    overflow: hidden;
    background: var(--hover);
    border-radius: 8px;
  }

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: opacity 0.15s;
  }

  a:hover img {
    opacity: 0.9;
  }

  .more {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-size: 13px;
    line-height: 20px;
    font-weight: 500;
    color: var(--ink);
    background: #FFFFFF;
    border: 1px solid var(--hairline);

    &:hover {
      border-color: #A3A3A3;
    }
  }
}

@media (max-width: 640px) {
  .photos {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

</style>
