import { ToastContainer as ToastifyContainer } from 'react-toastify'
import 'react-toastify/dist/ReactToastify.css'

export function ToastContainer() {
  return (
    <ToastifyContainer
      role='alert'
      aria-live='polite'
      position='top-right'
      autoClose={3000}
      hideProgressBar={false}
      newestOnTop={false}
      closeOnClick
      rtl={false}
      pauseOnFocusLoss
      draggable
      pauseOnHover
      theme='light'
      style={{
        top: '80px',
        zIndex: 9999,
      }}
    />
  )
}
