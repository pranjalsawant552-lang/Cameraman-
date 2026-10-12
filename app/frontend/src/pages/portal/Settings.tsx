import { Palette } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/primitives'
import { ThemeSwitcher } from '@/components/layout/ThemeSwitcher'
import { AccountSettings } from '@/components/account/AccountSettings'

export default function PortalSettings() {
  return (
    <div className="space-y-5">
      <AccountSettings />
      <Card className="max-w-2xl">
        <CardHeader><CardTitle className="flex items-center gap-2"><Palette className="h-4.5 w-4.5 text-accent" /> Appearance</CardTitle></CardHeader>
        <CardContent className="pt-3"><ThemeSwitcher variant="full" /></CardContent>
      </Card>
    </div>
  )
}
