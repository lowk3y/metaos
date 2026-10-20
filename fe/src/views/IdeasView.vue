<script setup lang="ts">
import { ref, watch } from 'vue'
import IdeaItem from '@/components/IdeaItem.vue'
import SignInPrompt from '@/components/SignInPrompt.vue'
import { EPISODE_FIELDS, type Idea } from '@/episodes'
import { errorMessage, graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// The episode ideas, the most recently added first; undefined while they load
const ideas = ref<Idea[]>()
const error = ref<string>()

// Only the latest load counts: an earlier one may answer after it
let loads = 0

// Ideas are the team's, so they load only for a signed-in user, and again when one signs in
watch(() => auth.authenticated, async (authenticated) => {
  const load = ++loads
  ideas.value = undefined
  error.value = undefined
  if (!authenticated) return
  try {
    const data = await graphql<{ ideas: Idea[] }>(`{ ideas { ${EPISODE_FIELDS} addedDate recommendedBy { name } } }`)
    if (load === loads) ideas.value = data.ideas
  } catch (e) {
    if (load === loads) error.value = errorMessage(e)
  }
}, { immediate: true })
</script>

<template>
  <main class="content">
    <SignInPrompt v-if="auth.ready && !auth.authenticated" title="Sign in to see the ideas">
      Episode ideas are for Ogrodje's team. Sign in with Discord or Google, or use MetaOS from your AI assistant over
      MCP: Usage shows how to connect it.
    </SignInPrompt>
    <p v-else-if="error" class="note">The ideas didn't load: {{ error }}</p>
    <p v-else-if="!ideas" class="note">Loading…</p>

    <!-- Once planned, an idea has a code and is listed with the episodes instead -->
    <section v-else aria-labelledby="ideas">
      <div class="heading">
        <h2 id="ideas">Ideas</h2>
        <span class="count">{{ ideas.length }}</span>
      </div>
      <p v-if="!ideas.length" class="note">No ideas yet.</p>
      <ul v-else class="episodes">
        <IdeaItem v-for="idea in ideas" :key="idea.id" :idea="idea"/>
      </ul>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '../assets/page';
</style>
