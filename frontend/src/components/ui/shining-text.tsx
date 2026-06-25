import { motion } from 'framer-motion'

interface ShiningTextProps {
  text: string
  className?: string
}

export function ShiningText({ text, className = '' }: ShiningTextProps) {
  return (
    <motion.span
      className={`bg-[linear-gradient(110deg,var(--text-muted)_0%,var(--text-muted)_35%,var(--brand-primary)_50%,var(--text-muted)_65%,var(--text-muted)_100%)] bg-[length:200%_100%] bg-clip-text text-transparent ${className}`}
      initial={{ backgroundPosition: '200% 0' }}
      animate={{ backgroundPosition: '-200% 0' }}
      transition={{
        repeat: Infinity,
        duration: 2,
        ease: 'linear',
      }}
    >
      {text}
    </motion.span>
  )
}
