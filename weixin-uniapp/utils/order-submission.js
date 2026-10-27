// One identifier belongs to one confirmed checkout intent, including explicit retries.
export function createClientRequestId() {
  return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const value = Math.floor(Math.random() * 16)
    return (char === 'x' ? value : (value & 3) | 8).toString(16)
  })
}

export function isDefiniteSubmissionFailure(error) {
  const status = Number(error && error.statusCode)
  if (status >= 500) return false
  if (status >= 400 && status < 500) return true
  if (status && (status < 200 || status >= 300)) return false
  return !!error && Number(error.code) === 0 && error.code != null
}
