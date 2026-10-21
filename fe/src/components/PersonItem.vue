<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import MarkedText from '@/components/MarkedText.vue'
import SocialLinks from '@/components/SocialLinks.vue'
import { episodePath } from '@/episodes'
import { type Person, personPath, rolesOf, socialLinks } from '@/people'
import { useAuthStore } from '@/stores/auth'

// Signed in, their name leads to their page, and their episodes to the episodes' pages
const auth = useAuthStore()

const props = defineProps<{
  person: Person
  // Found by a search: the words to mark
  words?: string[]
}>()

// Their latest episodes by code, and how many more there are: hosts have dozens
const LATEST = 3
const latest = computed(() => props.person.episodes.slice(0, LATEST).map(({ episode }) => episode))
const more = computed(() => props.person.episodes.length - latest.value.length)

const roles = computed(() => rolesOf(props.person))
const hasLinks = computed(() => socialLinks(props.person).length > 0)
</script>

<!-- A person: their name and who they are, then what they've been on the episodes and the latest of those, then where
     they're online. Like an idea's row (IdeaItem), the links where an episode has where to listen. -->
<template>
  <li class="person">
    <div class="main">
      <p class="title">
        <RouterLink v-if="auth.authenticated" :to="personPath(person)"><MarkedText :text="person.name" :words="words"/></RouterLink>
        <template v-else>{{ person.name }}</template>
      </p>
      <p v-if="person.bio" class="summary"><MarkedText :text="person.bio" :words="words"/></p>
      <p v-if="person.episodes.length" class="details">
        <span class="roles">{{ roles }}</span>
        <!-- An episode without a code, e.g. an idea, by its title -->
        <span>
          <template v-for="(episode, i) in latest" :key="episode.id">
            <template v-if="i">, </template>
            <RouterLink v-if="auth.authenticated" :class="{ code: episode.code }" :to="episodePath(episode)" :title="episode.title" lang="sl">{{ episode.code ?? episode.title }}</RouterLink>
            <span v-else :class="{ code: episode.code }" :title="episode.title" lang="sl">{{ episode.code ?? episode.title }}</span>
          </template>
          <template v-if="more"> and {{ more }} more</template>
        </span>
      </p>
    </div>
    <div v-if="hasLinks" class="links"><SocialLinks :person="person"/></div>
  </li>
</template>

<style scoped lang="scss">
@use '../assets/episode';

.person {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  column-gap: 16px;
  padding: 18px 0;
  border-top: 1px solid var(--rule);
}

@include episode.summary-row;

.summary, .roles {
  color: var(--secondary);
}

// Among the details, in their size
.code {
  font-size: 12px;
  line-height: 20px;
}

.details a {
  color: var(--secondary);

  &:hover {
    color: var(--accent-text);
  }
}

@media (max-width: 640px) {
  .person {
    grid-template-columns: minmax(0, 1fr);
    padding: 16px 0 12px;
  }
}

</style>
