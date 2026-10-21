<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {RouterLink, RouterView, useRoute} from 'vue-router'
import {faDiscord, faGithub} from '@fortawesome/free-brands-svg-icons'
import {faHouse, faLightbulb, faMicrophone, faPlug, faUserGroup} from '@fortawesome/free-solid-svg-icons'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import SearchPalette from '@/components/SearchPalette.vue'
import UserMenu from '@/components/UserMenu.vue'
import {graphql} from '@/graphql'
import {useAuthStore} from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()

// Who MetaOS takes the signed-in user for, and their person, once one is linked to them by email
const name = ref<string>()
const person = ref<{ id: string } | null>(null)

interface Me {
  username: string | null
  person: { id: string, name: string } | null
}

watch(() => auth.authenticated, async (authenticated) => {
  name.value = auth.username
  person.value = null
  if (!authenticated) return
  try {
    const {me} = await graphql<{ me: Me | null }>('{ me { username person { id name } } }')
    name.value = me?.person?.name ?? me?.username ?? auth.username
    person.value = me?.person ?? null
  } catch (error) {
    console.error('MetaOS does not know the signed-in user', error)
  }
}, {immediate: true})

// The pages, as tabs in the order they're used; the team's only for signed-in users. A new section, e.g. Partners or
// Analytics, is another tab here.
const pages = computed(() => [
  {path: '/', title: 'Home', icon: faHouse},
  {path: '/episodes', title: 'Episodes', icon: faMicrophone},
  {path: '/ideas', title: 'Ideas', icon: faLightbulb, team: true},
  {path: '/people', title: 'People', icon: faUserGroup},
  {path: '/usage', title: 'Usage', icon: faPlug},
].filter((page) => !page.team || auth.authenticated))

// A page's tab is underlined on its own pages too: Episodes on an episode's, People on a person's
function current(path: string) {
  return path === '/' ? route.path === '/' : route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <!-- The logo, and at the right search and you; under them the pages, as tabs -->
  <header>
    <div class="top">
      <RouterLink class="logo" to="/" aria-label="Ogrodje MetaOS, home"><img src="/favicon.png" alt=""/></RouterLink>
      <div v-if="auth.ready" class="actions">
        <!-- Search is the team's, as most of what it finds is -->
        <SearchPalette v-if="auth.authenticated"/>
        <UserMenu :name="name" :person="person"/>
      </div>
    </div>

    <nav class="pages" aria-label="Main">
      <RouterLink v-for="page in pages" :key="page.path" :to="page.path" :class="{ current: current(page.path) }"
                  :aria-current="current(page.path) ? 'page' : undefined">
        <FontAwesomeIcon :icon="page.icon"/>{{ page.title }}
      </RouterLink>
    </nav>
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
}

// The page's column: the logo lines up with what's under it
.top {
  max-width: var(--content-width);
  height: 64px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.logo {
  display: flex;

  img {
    display: block;
    height: 34px;
  }
}

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

// Tabs whose first label, not its padding, lines up with the logo. When they don't fit, they scroll sideways and fade
// out at the edge.
.pages {
  max-width: var(--content-width);
  margin: 0 auto;
  padding: 0 14px;
  display: flex;
  overflow-x: auto;
  scrollbar-width: none;
  mask-image: linear-gradient(to right, #000 calc(100% - 24px), transparent);

  &::-webkit-scrollbar {
    display: none;
  }

  a {
    display: inline-flex;
    flex: none;
    align-items: center;
    gap: 8px;
    padding: 12px 10px;
    font-size: 14px;
    line-height: 20px;
    color: var(--secondary);
    white-space: nowrap;

    svg {
      font-size: 13px;
      color: var(--muted);
    }

    &.current {
      color: var(--ink);
      font-weight: 500;
      box-shadow: inset 0 -2px 0 var(--accent);

      svg {
        color: var(--accent-text);
      }
    }
  }
}

// Phones: the page's narrower gutter, and the tabs closer together, without their icons
@media (max-width: 640px) {
  .top {
    height: 56px;
    padding: 0 16px;
  }

  .logo img {
    height: 30px;
  }

  .actions {
    gap: 8px;
  }

  .pages {
    padding: 0 8px;

    a {
      padding: 10px 8px;

      svg {
        display: none;
      }
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
