import type { GameModeId } from './types'

export interface GameInfo {
  id: GameModeId
  name: string
  pitch: string
  players: string
  min: number
  colour: string
}

// Colours are referee-and-kit inspired: red card, yellow card, sky-blue kit, away pink.
export const GAMES: GameInfo[] = [
  { id: 'IMPOSTER', name: 'Imposter', pitch: "Everyone gets the same player. One of you doesn't. Clue, bluff, vote.", players: '3–10 players', min: 3, colour: 'var(--color-imposter)' },
  { id: 'TEN', name: 'Ten', pitch: 'A hidden list of ten. Take turns naming one. Three wrong and the list closes.', players: '2–10 players', min: 2, colour: 'var(--color-ten)' },
  { id: 'HIGHER_LOWER', name: 'Higher or lower', pitch: 'Goals, birthdays, founding years. Everyone guesses at once; fastest right gets a bonus.', players: '2–10 players', min: 2, colour: 'var(--color-hol)' },
  { id: 'HEADS_UP', name: 'Heads up', pitch: "One of you can't see the card. Everyone else describes it on the call. Sixty seconds.", players: '2–10 players', min: 2, colour: 'var(--color-headsup)' },
]

export const gameInfo = (id: GameModeId): GameInfo => GAMES.find((g) => g.id === id) ?? GAMES[0]

// Kit colours for players, by join order.
export const KITS = ['#E63946', '#FFC933', '#3BB9FF', '#FF5FA2', '#7CE0A3', '#B48CFF', '#FF8A3D', '#F3F6EE']
export const kit = (colour: number): string => KITS[((colour % KITS.length) + KITS.length) % KITS.length]
