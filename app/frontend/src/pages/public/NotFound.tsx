import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowLeft, Aperture } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Bokeh } from '@/components/public/Bokeh'
import { Magnetic } from '@/components/public/motionKit'
import { usePageMeta } from '@/lib/usePageMeta'

/** The shot is out of focus — a 404 that racks into focus when it loads. */
export default function PublicNotFound() {
  usePageMeta('Page not found')
  return (
    <section className="relative flex min-h-[100svh] items-center justify-center overflow-hidden px-4 pt-24 text-center">
      <div className="aurora" aria-hidden><i /><i /><i /></div>
      <Bokeh count={30} />
      <div className="relative">
        <div className="relative mx-auto w-fit">
          <motion.h1
            initial={{ filter: 'blur(26px)', opacity: 0.4, scale: 1.06 }}
            animate={{ filter: 'blur(0px)', opacity: 1, scale: 1 }}
            transition={{ duration: 1.6, ease: [0.16, 1, 0.3, 1] }}
            className="font-display text-[28vw] leading-none text-gradient sm:text-[12rem]"
          >
            404
          </motion.h1>
          {/* focus reticle */}
          <motion.div aria-hidden className="absolute inset-[-6%]" initial={{ opacity: 0, scale: 1.3 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 1.2, duration: 0.5 }}>
            {['left-0 top-0 border-l-2 border-t-2', 'right-0 top-0 border-r-2 border-t-2', 'bottom-0 left-0 border-b-2 border-l-2', 'bottom-0 right-0 border-b-2 border-r-2'].map((c) => <span key={c} className={`absolute h-7 w-7 border-accent ${c}`} />)}
          </motion.div>
        </div>
        <p className="mt-6 font-display text-2xl text-ink sm:text-3xl">This page is out of focus</p>
        <p className="mx-auto mt-3 max-w-md text-ink-muted">We couldn't find what you were looking for. It may have moved, or the link might be mistyped.</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Magnetic><Link to="/"><Button size="lg" className="rounded-full"><ArrowLeft className="h-4 w-4" /> Back home</Button></Link></Magnetic>
          <Magnetic><Link to="/contact"><Button size="lg" variant="outline" className="rounded-full bg-surface/50 backdrop-blur"><Aperture className="h-4 w-4" /> Contact us</Button></Link></Magnetic>
        </div>
      </div>
    </section>
  )
}
