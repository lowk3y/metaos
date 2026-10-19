// Episodes as MetaOS's GraphQL API has them (src/main/resources/graphql/schema.graphqls)

export type Role = 'HOST' | 'CO_HOST' | 'GUEST' | 'PRODUCER' | 'CONSULTANT' | 'DESIGNER'

export type EpisodeStatus =
  | 'IDEA'
  | 'PLANNED'
  | 'SCHEDULED'
  | 'RECORDED'
  | 'IN_PRODUCTION'
  | 'READY'
  | 'EARLY_ACCESS'
  | 'PUBLISHED'
  | 'ON_HOLD'
  | 'CANCELLED'

export interface EpisodePerson {
  personId: string
  name: string
  roles: Role[]
}

/** A Google Doc linked to an episode or a session; only signed-in users get them */
export interface GoogleDoc {
  fileId: string
  url: string
}

export interface Session {
  id: string
  date: string
  location: string | null
  documents: GoogleDoc[]
}

export interface Episode {
  id: string
  code: string | null
  title: string
  status: EpisodeStatus
  session: Session | null
  recordingDate: string | null
  airedDate: string | null
  youtubeUrl: string | null
  spotifyUrl: string | null
  applePodcastsUrl: string | null
  patreonUrl: string | null
  people: EpisodePerson[]
  documents: GoogleDoc[]
}

/** The fields of an Episode, for a query */
export const EPISODE_FIELDS = `
  id code title status recordingDate airedDate youtubeUrl spotifyUrl applePodcastsUrl patreonUrl
  session { id date location documents { fileId url } }
  people { personId name roles }
  documents { fileId url }
`

const platforms = {
  YouTube: 'youtubeUrl',
  Spotify: 'spotifyUrl',
  'Apple Podcasts': 'applePodcastsUrl',
  Patreon: 'patreonUrl',
} as const

export type Platform = keyof typeof platforms

export function links(episode: Episode) {
  return (Object.keys(platforms) as Platform[]).flatMap((platform) => {
    const url = episode[platforms[platform]]
    return url ? [{ platform, url }] : []
  })
}

// CO_HOST -> co-host
export function roleName(role: Role) {
  return role.toLowerCase().replace('_', '-')
}

// IN_PRODUCTION -> in production
export function statusName(status: EpisodeStatus) {
  return status.toLowerCase().replace('_', ' ')
}

export function formatDate(date: string) {
  return new Date(date).toLocaleDateString(undefined, { dateStyle: 'medium' })
}

export interface SessionGroup {
  key: string
  session: Session | null
  episodes: Episode[]
}

/**
 * Episodes by their recording session, in the order they come in. Those without one come first, together, so that
 * when they're listed without a heading they don't look like part of the session above them.
 */
export function bySession(episodes: Episode[]): SessionGroup[] {
  const groups = new Map<string, SessionGroup>()
  for (const episode of episodes) {
    const key = episode.session?.id ?? 'none'
    const group = groups.get(key) ?? { key, session: episode.session, episodes: [] }
    group.episodes.push(episode)
    groups.set(key, group)
  }
  return [...groups.values()].sort((a, b) => Number(a.session !== null) - Number(b.session !== null))
}
