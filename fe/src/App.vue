<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { graphql } from '@/graphql'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// Who MetaOS takes the signed-in user for: their person, once one is linked to them by email
const name = ref<string>()

interface Me {
  username: string | null
  person: { name: string } | null
}

watch(() => auth.authenticated, async (authenticated) => {
  name.value = auth.username
  if (!authenticated) return
  try {
    const { me } = await graphql<{ me: Me | null }>('{ me { username person { name } } }')
    name.value = me?.person?.name ?? me?.username ?? auth.username
  } catch (error) {
    console.error('MetaOS does not know the signed-in user', error)
  }
}, { immediate: true })
</script>

<template>
  <header>

    <div class="wrapper">
      <nav>
        <RouterLink to="/">Home</RouterLink>
        <RouterLink to="/about">About</RouterLink>
      </nav>

      <div v-if="auth.ready" class="auth">
        <template v-if="auth.authenticated">
          <span>{{ name }}</span>
          <button @click="auth.logout()">Sign out</button>
        </template>
        <template v-else>
          <button @click="auth.login('discord')">Sign in with Discord</button>
          <button @click="auth.login('google')">Sign in with Google</button>
        </template>
      </div>
    </div>
  </header>

  <RouterView/>
</template>

<style scoped lang="scss">

header {
  .wrapper {
    padding: 3px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 2px solid #CCC;

    nav a {
      padding-left: 10px;
      padding-right: 10px;

      text-decoration: none;
    }

    .auth {
      display: flex;
      gap: 8px;
      align-items: center;
    }
  }
}

</style>
