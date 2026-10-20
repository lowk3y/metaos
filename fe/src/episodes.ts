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

/** A photo in a Koofr folder: its thumbnail, from MetaOS with the sign-in token, and its folder in Koofr */
export interface KoofrPhoto {
  name: string
  path: string
  thumbnail: string
  url: string
}

/** A Koofr folder shared with Ogrodje's bot: a place, by name when Koofr answers, a path in it, and where it opens */
export interface KoofrFolder {
  place: string
  placeName: string | null
  path: string
  url: string
  photos: KoofrPhoto[]
}

/** One field of a change; from and to are null when there's nothing short to show, e.g. a summary */
export interface FieldChange {
  field: string
  from: string | null
  to: string | null
}

/** A change from the audit log, told as what happened */
export interface Change {
  at: string
  actor: string | null
  client: string | null
  tool: string | null
  summary: string
  fields: FieldChange[]
}

/** An episode with everything about it, for its page */
export interface EpisodeDetails extends Episode {
  show: string | null
  summary: string | null
  machineSummary: string | null
  addedDate: string
  koofrFolders: KoofrFolder[]
  history: Change[]
}

/** A session with everything about it, for its page */
export interface SessionDetails extends Session {
  address: string | null
  episodes: Episode[]
  koofrFolders: KoofrFolder[]
  history: Change[]
}

// Seven photos and a way to the rest fill two rows of four
export const PHOTOS = 7

export const KOOFR_FIELDS = `koofrFolders { place placeName path url photos(limit: ${PHOTOS}) { name path thumbnail url } }`

/** The first few photos of an episode's or session's Koofr folders */
export function photosOf(folders: KoofrFolder[], limit = PHOTOS) {
  return folders.flatMap((folder) => folder.photos).slice(0, limit)
}
export const HISTORY_FIELDS = 'history { at actor client tool summary fields { field from to } }'

/** An episode's page: at its code, or at its ID while it has none */
export function episodePath(episode: Pick<Episode, 'id' | 'code'>) {
  return `/episodes/${encodeURIComponent(episode.code ?? episode.id)}`
}

export function sessionPath(session: Pick<Session, 'id'>) {
  return `/sessions/${session.id}`
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

/** A session as people know it: when and where, e.g. 22 Oct 2026 · AWS Community Day Adria */
export function sessionTitle(session: Pick<Session, 'date' | 'location'>) {
  return [formatDate(session.date), session.location].filter(Boolean).join(' · ')
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
