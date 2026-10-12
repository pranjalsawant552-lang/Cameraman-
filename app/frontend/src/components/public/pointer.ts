import { useEffect, useState } from 'react'

/** True on touch-first devices, where hover/tilt effects would only get in the way. */
export function useCoarsePointer() {
  const [coarse, setCoarse] = useState(() => typeof window !== 'undefined' && window.matchMedia('(pointer: coarse)').matches)
  useEffect(() => {
    const mq = window.matchMedia('(pointer: coarse)')
    const on = () => setCoarse(mq.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])
  return coarse
}

/** Sets --sx/--sy on a `.spotlight` element so its CSS glow follows the pointer. */
export function spotlightHandlers(): { onPointerMove: (e: React.PointerEvent<HTMLElement>) => void } {
  return {
    onPointerMove: (e) => {
      const r = e.currentTarget.getBoundingClientRect()
      e.currentTarget.style.setProperty('--sx', `${e.clientX - r.left}px`)
      e.currentTarget.style.setProperty('--sy', `${e.clientY - r.top}px`)
    },
  }
}
