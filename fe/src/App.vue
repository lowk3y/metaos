<script setup lang="ts">
import {ref, watch} from 'vue'
import {RouterLink, RouterView} from 'vue-router'
import {faDiscord, faGithub, faGoogle} from '@fortawesome/free-brands-svg-icons'
import {faArrowRightFromBracket} from '@fortawesome/free-solid-svg-icons'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import SearchPalette from '@/components/SearchPalette.vue'
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

    <!-- On phones the pages go on a line of their own, under the logo and signing in or out -->
    <div class="wrapper">
      <RouterLink class="logo" to="/" aria-label="Ogrodje MetaOS, home"><img src="/favicon.png" alt=""/></RouterLink>

      <nav class="pages" aria-label="Main">
        <RouterLink to="/">Home</RouterLink>
        <RouterLink to="/episodes">Episodes</RouterLink>
        <!-- The team's, so only for signed-in users -->
        <RouterLink v-if="auth.authenticated" to="/ideas">Ideas</RouterLink>
        <RouterLink to="/people">People</RouterLink>
        <RouterLink to="/usage">Usage</RouterLink>
      </nav>

      <div v-if="auth.ready" class="auth">
        <!-- Search is the team's, as most of what it finds is -->
        <template v-if="auth.authenticated">
          <SearchPalette/>
          <span class="name">{{ name }}</span>
          <button type="button" class="button" @click="auth.logout()">
            <FontAwesomeIcon :icon="faArrowRightFromBracket"/>Sign out
          </button>
        </template>
        <!-- On phones only the providers' names, so both fit beside the logo -->
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

  <!-- Takes the height the header and footer leave, so the footer stays at the bottom of short pages -->
  <div class="page">
    <RouterView/>
  </div>

  <footer>
    <div class="wrapper">
      <span class="name">Ogrodje MetaOS</span>
      <nav class="elsewhere" aria-label="Ogrodje elsewhere">
        <a class="icon-link" href="https://github.com/ogrodje/metaos" title="MetaOS on GitHub" aria-label="MetaOS on GitHub">
          <FontAwesomeIcon :icon="faGithub"/>
        </a>
        <a class="icon-link" href="https://discord.gg/xV2Xc4VX9W" title="Ogrodje on Discord" aria-label="Ogrodje on Discord">
          <FontAwesomeIcon :icon="faDiscord"/>
        </a>
      </nav>
    </div>
  </footer>
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
    align-items: center;
    column-gap: 20px;
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

  // At the right
  .auth {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-left: auto;
  }

  .name {
    font-size: 13px;
    line-height: 20px;
    color: var(--secondary);
  }

  // Narrower than 800 px, the pages and signing in don't both fit beside the logo: the logo and signing in or out go on
  // a line, and under them the pages, as tabs whose first label, not its padding, lines up with the logo
  @media (max-width: 800px) {
    .wrapper {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      grid-template-rows: 60px auto;
      grid-template-areas: 'logo auth' 'pages pages';
      min-height: 0;
    }

    .logo {
      grid-area: logo;
    }

    .pages {
      grid-area: pages;
      margin-left: -10px;

      a {
        padding: 10px;
      }
    }

    .auth {
      grid-area: auth;
      justify-self: end;
    }
  }

  // Phones: the page's narrower gutter, everything a little closer together, and only the providers' names
  @media (max-width: 640px) {
    .wrapper {
      grid-template-rows: 56px auto;
      column-gap: 12px;
      padding: 0 16px;
    }

    .logo img {
      height: 30px;
    }

    .pages {
      margin-left: -8px;

      a {
        padding: 10px 8px;
      }
    }

    .auth {
      gap: 8px;
    }

    .name, .long {
      display: none;
    }
  }
}

:global(#app) {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.page {
  flex: 1;
}

// The header's container, without its line
footer {
  .wrapper {
    max-width: var(--content-width);
    min-height: 64px;
    margin: 0 auto;
    padding: 0 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 24px;
  }

  .name {
    font-size: 13px;
    line-height: 20px;
    color: var(--muted);
  }

  .elsewhere {
    display: flex;
    gap: 4px;
    // The glyphs, not their hit areas, line up with the content's edge
    margin-right: var(--icon-link-inset);

    a {
      color: var(--secondary);
    }
  }

  @media (max-width: 640px) {
    .wrapper {
      padding: 0 16px;
    }
  }
}

</style>
