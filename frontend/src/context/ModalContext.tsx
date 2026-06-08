import { createContext, useContext, useState } from 'react'
import type { ReactNode } from 'react'

interface ModalState {
  showModelSettings: boolean
  showMemoryPanel: boolean
}

interface ModalContextType extends ModalState {
  openModelSettings: () => void
  closeModelSettings: () => void
  openMemoryPanel: () => void
  closeMemoryPanel: () => void
}

const ModalContext = createContext<ModalContextType | undefined>(undefined)

export function ModalProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<ModalState>({
    showModelSettings: false,
    showMemoryPanel: false,
  })

  const openModelSettings = () => setState((prev) => ({ ...prev, showModelSettings: true }))
  const closeModelSettings = () => setState((prev) => ({ ...prev, showModelSettings: false }))
  const openMemoryPanel = () => setState((prev) => ({ ...prev, showMemoryPanel: true }))
  const closeMemoryPanel = () => setState((prev) => ({ ...prev, showMemoryPanel: false }))

  return (
    <ModalContext.Provider
      value={{
        ...state,
        openModelSettings,
        closeModelSettings,
        openMemoryPanel,
        closeMemoryPanel,
      }}
    >
      {children}
    </ModalContext.Provider>
  )
}

export function useModal() {
  const context = useContext(ModalContext)
  if (!context) {
    throw new Error('useModal must be used within ModalProvider')
  }
  return context
}
