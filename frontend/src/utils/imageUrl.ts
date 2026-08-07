export function toAccessibleImageUrl(url: string | undefined): string {
  if (!url) return url
  try {
    const parsed = new URL(url, window.location.origin)
    if (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1') {
      return parsed.pathname + parsed.search + parsed.hash
    }
    return url
  } catch {
    return url
  }
}
