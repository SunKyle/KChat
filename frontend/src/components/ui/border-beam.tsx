interface BorderBeamProps {
  className?: string
  size?: number
  duration?: number
  borderWidth?: number
  anchor?: number
  colorFrom?: string
  colorTo?: string
  delay?: number
}

export const BorderBeam = ({
  className,
  size = 80,
  duration = 4,
  anchor = 90,
  borderWidth = 1.5,
  colorFrom = '#0ea5e9',
  colorTo = '#a78bfa',
  delay = 0,
}: BorderBeamProps) => {
  return (
    <div
      className={`pointer-events-none absolute rounded-[inherit] ${className ?? ''}`}
      style={{
        inset: `${-borderWidth}px`,
        border: `${borderWidth}px solid transparent`,
        maskClip: 'padding-box, border-box',
        maskComposite: 'intersect',
        WebkitMaskClip: 'padding-box, border-box',
        WebkitMaskComposite: 'intersect',
        maskImage: 'linear-gradient(transparent, transparent), linear-gradient(white, white)',
        WebkitMaskImage: 'linear-gradient(transparent, transparent), linear-gradient(white, white)',
      }}
    >
      <div
        className="absolute"
        style={{
          width: `${size}px`,
          height: `${size}px`,
          background: `linear-gradient(to left, ${colorFrom}, ${colorTo}, transparent)`,
          offsetAnchor: `${anchor}% 50%`,
          offsetPath: `rect(0 auto auto 0 round 10px)`,
          animation: `border-beam-travel ${duration}s linear ${-delay}s infinite`,
        }}
      />
    </div>
  )
}
