import { useAuthStore } from '@/stores/auth'

/** What went wrong, for the page to say */
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error)
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
