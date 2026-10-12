import { useEffect } from 'react'
import { STUDIO_NAME } from '@/lib/branding'

/** Sets the tab title and meta description for a public page, restoring them on leave. */
export function usePageMeta(title: string, description?: string) {
  useEffect(() => {
    const prevTitle = document.title
    document.title = `${title} · ${STUDIO_NAME}`

    let tag = document.querySelector<HTMLMetaElement>('meta[name="description"]')
    const prevDesc = tag?.content
    if (description) {
      if (!tag) {
        tag = document.createElement('meta')
        tag.name = 'description'
        document.head.appendChild(tag)
      }
      tag.content = description
    }
    return () => {
      document.title = prevTitle
      if (tag && prevDesc !== undefined) tag.content = prevDesc
    }
  }, [title, description])
}
