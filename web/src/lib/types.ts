// Mirrors the server's records exactly. Times are epoch millis.

export type GameModeId = 'IMPOSTER' | 'TEN' | 'HIGHER_LOWER' | 'HEADS_UP'
export type Status = 'LOBBY' | 'PLAYING' | 'FINISHED'

export interface PlayerView {
  id: string
  name: string
  colour: number
  host: boolean
  connected: boolean
  score: number
}

export interface RoomSnapshot {
  code: string
  mode: GameModeId
  status: Status
  stage: string | null
  stageEndsAt: number
  stageDurationMs: number
  hostId: string
  players: PlayerView[]
}

// ---------------------------------------------------------------- imposter

export interface Clue { playerId: string; word: string | null; pass: number }

export interface ImposterReveal {
  imposterId: string
  secret: string
  decoy: string
  accusedId: string | null
  caught: boolean
  stealGuess: string | null
  stealOk: boolean
  votes: Record<string, string>
  gained: Record<string, number>
}

export interface ImposterView {
  round: number
  rounds: number
  yourWord: string | null
  turnPlayerId: string | null
  yourTurn: boolean
  pass: number
  passes: number
  clues: Clue[]
  voted: string[]
  yourVote: string | null
  youMustSteal: boolean
  reveal: ImposterReveal | null
}

// --------------------------------------------------------------------- ten

export interface TenSlot { points: number; answer: string | null; foundBy: string | null }
export interface TenEntry { playerId: string; text: string | null; kind: 'HIT' | 'MISS' | 'TIMEOUT' | 'SKIP'; points: number }

export interface TenView {
  round: number
  rounds: number
  question: string
  slots: TenSlot[]
  strikes: number
  maxStrikes: number
  turnPlayerId: string | null
  yourTurn: boolean
  log: TenEntry[]
  revealed: boolean
  cleared: boolean
  gained: Record<string, number>
}

// ------------------------------------------------------------ higher/lower

export interface HolSide { name: string; value: number | null }

export interface HolView {
  round: number
  rounds: number
  step: number
  steps: number
  category: string
  question: string
  higherWord: string
  lowerWord: string
  left: HolSide
  right: HolSide
  yourPick: 'HIGHER' | 'LOWER' | null
  lockedIn: string[]
  revealed: boolean
  answer: 'HIGHER' | 'LOWER' | 'EITHER' | null
  picks: Record<string, 'HIGHER' | 'LOWER'>
  fastestId: string | null
  gained: Record<string, number>
}

// ---------------------------------------------------------------- heads up

export interface HeadsUpDone { name: string; result: 'GOT' | 'PASS' }

export interface HeadsUpView {
  turn: number
  turns: number
  guesserId: string | null
  youAreGuesser: boolean
  card: string | null
  cardKind: string | null
  cardNo: number
  history: HeadsUpDone[]
  gotThisTurn: number
}

export type GameView = ImposterView | TenView | HolView | HeadsUpView

export interface StatePayload {
  room: RoomSnapshot
  now: number
  game: GameView | null
}

export interface Identity {
  roomCode: string
  playerId: string
  playerToken: string
}
