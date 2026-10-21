export interface ClientEnvironment {
  baseApi: string
  socketUrl: string
  deletePermissions: boolean
}

export function readClientEnvironment(env: Record<string, string | undefined>): ClientEnvironment {
  return Object.freeze({
    baseApi: env.VITE_BASE_API || '/api',
    socketUrl: env.VITE_SOCKET_URL ?? '/ws/',
    // Vite exposes these values as strings; Boolean('false') would grant access.
    deletePermissions: env.VITE_DELETE_PERMISSIONS === 'true',
  })
}
