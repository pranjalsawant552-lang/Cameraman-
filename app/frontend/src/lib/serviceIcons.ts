import { Baby, Building2, Camera, Heart, MapPinned, Users, Aperture, Star, Gift, Sparkles, type LucideIcon } from 'lucide-react'

/** Fixed set of icon choices for the admin-editable "What we shoot" list on
 *  the public home page — stored by name (a plain string) in
 *  StudioSettings.servicesJson, resolved to a component here. Keeping the
 *  set fixed (rather than letting admin type any lucide icon name) avoids
 *  ever rendering nothing because of a typo. */
export const SERVICE_ICONS: Record<string, LucideIcon> = {
  camera: Camera,
  heart: Heart,
  baby: Baby,
  location: MapPinned,
  building: Building2,
  users: Users,
  aperture: Aperture,
  star: Star,
  gift: Gift,
  sparkles: Sparkles,
}

export const SERVICE_ICON_OPTIONS = Object.keys(SERVICE_ICONS)

export function resolveServiceIcon(name: string): LucideIcon {
  return SERVICE_ICONS[name] ?? Camera
}
