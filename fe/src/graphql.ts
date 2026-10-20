import { useAuthStore } from '@/stores/auth'

/** What went wrong, for the page to say */
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error)
}

/** A file from MetaOS that needs the sign-in token, e.g. a thumbnail, as a URL to show it from memory; revoke it after */
export async function fetchObjectUrl(path: string): Promise<string> {
  const token = await useAuthStore().token()
  const response = await fetch(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!response.ok) throw new Error(`MetaOS answered ${response.status}`)
  return URL.createObjectURL(await response.blob())
}

/** Asks MetaOS's GraphQL API (/graphql), as the signed-in user when there is one */
export async function graphql<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const token = await useAuthStore().token()
  const response = await fetch('/graphql', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // Errors come in the body, with a 200
      Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ query, variables }),
  })
  if (!response.ok) throw new Error(`MetaOS answered ${response.status}`)

  const { data, errors } = await response.json()
  if (errors?.length) throw new Error(errors.map((error: { message: string }) => error.message).join('; '))
  return data as T
}
