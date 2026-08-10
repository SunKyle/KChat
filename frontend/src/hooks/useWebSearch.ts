import { useLocalStorage } from './useLocalStorage'

export function useWebSearch() {
  const [webSearchEnabled, setWebSearchEnabled] = useLocalStorage<boolean>(
    'kchat_web_search',
    false
  )

  const toggleWebSearch = () => setWebSearchEnabled((prev) => !prev)

  return { webSearchEnabled, toggleWebSearch, setWebSearchEnabled } as const
}
