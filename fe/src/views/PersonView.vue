<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import EpisodeItem from '@/components/EpisodeItem.vue'
import EpisodeRow from '@/components/EpisodeRow.vue'
import SignInPrompt from '@/components/SignInPrompt.vue'
import SocialLinks from '@/components/SocialLinks.vue'
import { type Episode, EPISODE_FIELDS, type Role, roleName } from '@/episodes'
import { errorMessage, graphql } from '@/graphql'
import { type Person, rolesOf, socialLinks } from '@/people'
import { useAuthStore } from '@/stores/auth'

/** A person with everything about them, for their page: their episodes whole, and those they recommended */
interface PersonDetails extends Omit<Person, 'episodes'> {
  episodes: { episode: Episode, roles: Role[] }[]
  recommended: Episode[]
}

const auth = useAuthStore()
const route = useRoute()

// Undefined while it loads; null when there's no such person
const person = ref<PersonDetails | null>()
const error = ref<string>()

// Only the latest load counts: an earlier one may answer after it
let loads = 0

// The person in the path: again when the path changes, or the user signs in
watch([() => route.params.id, () => auth.authenticated], async ([id, authenticated]) => {
  const load = ++loads
  person.value = undefined
  error.value = undefined
  if (!authenticated) return
  try {
    const data = await graphql<{ person: PersonDetails | null }>(`query ($id: ID!) {
      person(id: $id) {
        id name bio homepageUrl linkedinUrl twitterUrl instagramUrl facebookUrl
        episodes { episode { ${EPISODE_FIELDS} } roles }
        recommended { ${EPISODE_FIELDS} }
      }
    }`, { id: String(id) })
    if (load === loads) person.value = data.person
  } catch (e) {
    if (load === loads) error.value = errorMessage(e)
  }
}, { immediate: true })

// In the order an episode's people are listed
const ROLES: Role[] = ['HOST', 'CO_HOST', 'GUEST', 'PRODUCER', 'CONSULTANT', 'DESIGNER', 'CANDIDATE']

// Their episodes by what they are on them, hosts first; one they're two things on is under both
const byRole = computed(() => ROLES.flatMap((role) => {
  const episodes = (person.value?.episodes ?? []).filter((on) => on.roles.includes(role)).map((on) => on.episode)
  return episodes.length ? [{ role, title: `As ${roleName(role)}`, episodes }] : []
}))

// Host on 61, guest on 2
const onEpisodes = computed(() => byRole.value
  .map(({ role, episodes }) => `${roleName(role)} on ${episodes.length}`)
  .join(', ')
  .replace(/^./, (first) => first.toUpperCase()))

const hasLinks = computed(() => (person.value ? socialLinks(person.value).length > 0 : false))
</script>

<template>
  <main class="content">
    <SignInPrompt v-if="auth.ready && !auth.authenticated" title="Sign in to see this person">
      A person's page, with every episode they're on, is for Ogrodje's team. Sign in with Discord or Google, or use
      MetaOS from your AI assistant over MCP: Usage shows how to connect it.
    </SignInPrompt>
    <p v-else-if="error" class="note">The person didn't load: {{ error }}</p>
    <p v-else-if="person === null" class="note">There's no such person.</p>
    <p v-else-if="!person" class="note">Loading…</p>

    <template v-else>
      <header class="title">
        <p class="kicker">{{ person.episodes.length ? rolesOf(person) : 'Person' }}</p>
        <h1>{{ person.name }}</h1>
        <p v-if="person.bio" class="subtitle">{{ person.bio }}</p>
      </header>

      <section v-if="hasLinks || person.homepageUrl || byRole.length || person.recommended.length" aria-labelledby="details">
        <div class="heading"><h2 id="details">Details</h2></div>
        <dl class="details">
          <div v-if="person.homepageUrl">
            <dt>Website</dt>
            <dd><a class="link" :href="person.homepageUrl">{{ person.homepageUrl.replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, '') }}</a></dd>
          </div>
          <div v-if="hasLinks">
            <dt>Online</dt>
            <dd class="icons"><SocialLinks :person="person"/></dd>
          </div>
          <div v-if="byRole.length">
            <dt>Episodes</dt>
            <dd>{{ onEpisodes }}</dd>
          </div>
          <div v-if="person.recommended.length">
            <dt>Recommended</dt>
            <dd>{{ person.recommended.length }} {{ person.recommended.length === 1 ? 'episode' : 'episodes' }}</dd>
          </div>
        </dl>
      </section>

      <p v-if="!byRole.length && !person.recommended.length" class="note">Not on any episode yet.</p>

      <!-- Each as the other lists have it: a line while it's in the works, its summary once it's released -->
      <section v-for="group in byRole" :key="group.role" :aria-labelledby="`as-${group.role}`">
        <div class="heading">
          <h2 :id="`as-${group.role}`">{{ group.title }}</h2>
          <span class="count">{{ group.episodes.length }}</span>
        </div>
        <ul class="episodes">
          <template v-for="episode in group.episodes" :key="episode.id">
            <EpisodeItem v-if="episode.status === 'PUBLISHED'" :episode="episode"/>
            <EpisodeRow v-else :episode="episode"/>
          </template>
        </ul>
      </section>

      <section v-if="person.recommended.length" aria-labelledby="recommended">
        <div class="heading">
          <h2 id="recommended">Recommended</h2>
          <span class="count">{{ person.recommended.length }}</span>
        </div>
        <ul class="episodes">
          <template v-for="episode in person.recommended" :key="episode.id">
            <EpisodeItem v-if="episode.status === 'PUBLISHED'" :episode="episode"/>
            <EpisodeRow v-else :episode="episode"/>
          </template>
        </ul>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">
@use '../assets/page';

.link {
  color: var(--accent-text);

  &:hover {
    text-decoration: underline;
  }
}
</style>
