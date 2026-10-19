import { ref } from 'vue'
import { defineStore } from 'pinia'
import Keycloak from 'keycloak-js'

/** Realm metaos's identity providers (keycloak/discord_idp.sh, keycloak/google_idp.sh) */
export type IdentityProvider = 'discord' | 'google'

/** Where users sign in, from MetaOS's /config.json (Frontend.kt), so one build works with every Keycloak */
interface FrontendConfig {
  issuer: string
  clientId: string
}

/** The signed-in user, through Keycloak's client metaos-ui */
export const useAuthStore = defineStore('auth', () => {
  let keycloak: Keycloak | undefined
  // Whether it's known yet if the user is signed in
  const ready = ref(false)
  const authenticated = ref(false)
  const username = ref<string>()

  async function init() {
    try {
      const config: FrontendConfig = await (await fetch('/config.json')).json()
      // An issuer is <Keycloak's URL>/realms/<realm>
      const [, url, realm] = config.issuer.match(/^(.+)\/realms\/([^/]+)$/) ?? []
      if (!url || !realm) throw new Error(`Unexpected issuer ${config.issuer}`)

      keycloak = new Keycloak({ url, realm, clientId: config.clientId })
      authenticated.value = await keycloak.init({
        // Signed in already? Asked in a hidden iframe (public/silent-check-sso.html), so the page stays where it is
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
        // A sign-out elsewhere shows when the token can't be refreshed any more
        checkLoginIframe: false,
      })
      username.value = keycloak.tokenParsed?.preferred_username
    } catch (error) {
      console.error('Signing in is unavailable', error)
    } finally {
      ready.value = true
    }
  }

  /** Straight to the identity provider, past Keycloak's own login page, and back to this page */
  function login(idpHint: IdentityProvider) {
    keycloak?.login({ idpHint })
  }

  function logout() {
    keycloak?.logout({ redirectUri: `${location.origin}/` })
  }

  /** An access token for MetaOS's API, refreshed when it's about to expire; none when signed out */
  async function token(): Promise<string | undefined> {
    if (!keycloak?.authenticated) return undefined
    try {
      await keycloak.updateToken(30)
    } catch {
      // The Keycloak session is over
      keycloak.clearToken()
      authenticated.value = false
      return undefined
    }
    return keycloak.token
  }

  return { ready, authenticated, username, init, login, logout, token }
})
