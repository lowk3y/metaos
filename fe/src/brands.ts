import type { IconDefinition, IconName } from '@fortawesome/fontawesome-svg-core'
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

/** Koofr's logo, which Font Awesome hasn't got, as one of its icons ('fak' is its prefix for custom ones) */
export const faKoofr: IconDefinition = {
  prefix: 'fak',
  iconName: 'koofr' as string as IconName,
  icon: [18, 18, [], '', 'M11.318 1c.334.026.771.154 1.151.522.314.304.44.635.5 1.048l.01.323-.001 1.167H17v6.115h-3.712a3.932 3.932 0 01-3.932-3.932l-.001-1.47V2.777h-.71L8.642 4.06l.001 2.183a3.932 3.932 0 01-3.932 3.932H1V4.06l4.042-.001V2.893l.011-.323c.059-.413.185-.744.5-1.048.38-.368.816-.496 1.15-.522h4.615zM1 10.886V17h7.644v-2.182a3.932 3.932 0 00-3.932-3.932H1zm12.288 0a3.932 3.932 0 00-3.932 3.932V17H17v-6.114h-3.712z'],
}
