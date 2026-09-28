import { kit } from '../lib/games'

interface Props {
  colour: number
  label?: string
  size?: number
  captain?: boolean
  dim?: boolean
  ring?: string | null
  title?: string
}

/** A player, drawn as their kit. The host wears the captain's armband. */
export function Shirt({ colour, label, size = 44, captain = false, dim = false, ring = null, title }: Props) {
  const fill = kit(colour)
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      role="img"
      aria-label={title}
      style={{ opacity: dim ? 0.35 : 1, overflow: 'visible', flexShrink: 0 }}
    >
      {title && <title>{title}</title>}
      {ring && <circle cx="50" cy="52" r="50" fill="none" stroke={ring} strokeWidth="5" />}
      <path
        d="M34 10 L22 15 L6 30 L16 44 L26 38 L26 90 Q50 95 74 90 L74 38 L84 44 L94 30 L78 15 L66 10 Q50 24 34 10 Z"
        fill={fill}
        stroke="#0A1F17"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path d="M34 10 Q50 24 66 10" fill="none" stroke="#0A1F17" strokeWidth="3" />
      {captain && <polygon points="10.5,25.8 15.6,21 23.4,39.6 18.2,42.7" fill="#0A1F17" />}
      {label && (
        <text
          x="50"
          y="70"
          textAnchor="middle"
          fontFamily="Big Shoulders Display, Arial Narrow, sans-serif"
          fontWeight="900"
          fontSize={label.length > 2 ? 26 : 34}
          fill="#0A1F17"
        >
          {label}
        </text>
      )}
    </svg>
  )
}
