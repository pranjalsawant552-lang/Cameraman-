import { describe, expect, it } from 'vitest'
import { moveItem } from './AlbumPhotosModal'

describe('moveItem', () => {
  it('moves an item forwards and backwards', () => {
    expect(moveItem(['a', 'b', 'c', 'd'], 0, 2)).toEqual(['b', 'c', 'a', 'd'])
    expect(moveItem(['a', 'b', 'c', 'd'], 3, 1)).toEqual(['a', 'd', 'b', 'c'])
  })
  it('returns the same list for no-op or out-of-range moves', () => {
    const list = ['a', 'b']
    expect(moveItem(list, 1, 1)).toBe(list)
    expect(moveItem(list, -1, 0)).toBe(list)
    expect(moveItem(list, 0, 5)).toBe(list)
  })
  it('does not mutate its input', () => {
    const list = ['a', 'b', 'c']
    moveItem(list, 0, 2)
    expect(list).toEqual(['a', 'b', 'c'])
  })
})
