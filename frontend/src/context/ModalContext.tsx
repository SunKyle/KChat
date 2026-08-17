import { createContext, useContext, useState } from 'react'
import type { ReactNode } from 'react'

interface ModalState {
  showModelSettings: boolean
}

interface ModalContextType extends ModalState {
  openModelSettings: () => void
  closeModelSettings: () => void
}

const ModalContext = createContext<ModalContextType | undefined>(undefined)

export function ModalProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<ModalState>({
    showModelSettings: false,
  })

  const openModelSettings = () => setState((prev) => ({ ...prev, showModelSettings: true }))
  const closeModelSettings = () => setState((prev) => ({ ...prev, showModelSettings: false }))

  return (
    <ModalContext.Provider
      value={{
        ...state,
        openModelSettings,
        closeModelSettings,
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