import type { CSSProperties, ReactNode } from 'react'

interface Props {
  children: ReactNode
  hot?: boolean
  size?: number
  className?: string
  label?: string
  style?: CSSProperties
}

/** The fourth official's board. Used for the room code and every countdown. */
export function Led({ children, hot = false, size = 22, className = '', label, style }: Props) {
  return (
    <span
      className={`led ${hot ? 'led-hot' : ''} ${className}`}
      style={{ fontSize: size, padding: `${size * 0.32}px ${size * 0.45}px`, ...style }}
      aria-label={label}
    >
      {children}
    </span>
  )
}
