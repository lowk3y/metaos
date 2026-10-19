<script setup lang="ts">
import {ref, watch} from 'vue'
import {RouterLink, RouterView} from 'vue-router'
import {faDiscord, faGoogle} from '@fortawesome/free-brands-svg-icons'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import {graphql} from '@/graphql'
import {useAuthStore} from '@/stores/auth'

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
    const {me} = await graphql<{ me: Me | null }>('{ me { username person { name } } }')
    name.value = me?.person?.name ?? me?.username ?? auth.username
  } catch (error) {
    console.error('MetaOS does not know the signed-in user', error)
  }
}, {immediate: true})
</script>

<template>
  <header>

    <div class="wrapper">
      <nav aria-label="Main">
        <RouterLink class="logo" to="/" aria-label="Ogrodje MetaOS, home"><img src="/favicon.png" alt=""/></RouterLink>

        <div class="pages">
          <RouterLink to="/">Home</RouterLink>
          <RouterLink to="/usage">Usage</RouterLink>
        </div>
      </nav>

      <div v-if="auth.ready" class="auth">
        <template v-if="auth.authenticated">
          <span class="name">{{ name }}</span>
          <button type="button" class="button" @click="auth.logout()">Sign out</button>
        </template>
        <!-- On phones only the providers' names, so both fit -->
        <template v-else>
          <button type="button" class="button" @click="auth.login('discord')">
            <FontAwesomeIcon :icon="faDiscord"/><span><span class="long">Sign in with </span>Discord</span>
          </button>
          <button type="button" class="button" @click="auth.login('google')">
            <FontAwesomeIcon :icon="faGoogle"/><span><span class="long">Sign in with </span>Google</span>
          </button>
        </template>
      </div>
    </div>
  </header>

  <RouterView/>
</template>

<style scoped lang="scss">

header {
  border-bottom: 1px solid var(--hairline);

  .wrapper {
    max-width: var(--content-width);
    min-height: 64px;
    margin: 0 auto;
    padding: 0 24px;
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    column-gap: 24px;
  }

  nav {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .logo {
    display: flex;

    img {
      display: block;
      height: 34px;
    }
  }

  .pages {
    display: flex;

    a {
      padding: 22px 10px;
      font-size: 14px;
      line-height: 20px;
      color: var(--secondary);

      &.router-link-exact-active {
        color: var(--ink);
        font-weight: 500;
        box-shadow: inset 0 -2px 0 var(--accent);
      }
    }
  }

  .auth {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  .name {
    font-size: 13px;
    line-height: 20px;
    color: var(--secondary);
  }

  @media (max-width: 640px) {
    .name, .long {
      display: none;
    }
  }
}

</style>
