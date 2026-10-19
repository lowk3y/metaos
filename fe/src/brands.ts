import type { IconDefinition } from '@fortawesome/fontawesome-svg-core'
import { faPatreon, faSpotify, faYoutube } from '@fortawesome/free-brands-svg-icons'
import { faPodcast } from '@fortawesome/free-solid-svg-icons'
import type { Platform } from '@/episodes'

/** Where episodes are published, in their brand colours. Font Awesome has no Apple Podcasts logo; its podcast icon looks much like it. */
export const brands: Record<Platform, { icon: IconDefinition, color: string }> = {
  YouTube: { icon: faYoutube, color: '#FF0000' },
  Spotify: { icon: faSpotify, color: '#1DB954' },
  'Apple Podcasts': { icon: faPodcast, color: '#9933CC' },
  Patreon: { icon: faPatreon, color: '#FF424D' },
}
