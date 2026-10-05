import {
  Ban, ChevronRight, Clock, Eye, EyeOff, Globe, Grid2x2, Home, KeyRound, ListChecks, Lock, LucideIcon, Play,
  Plus, Search, Settings as SettingsIcon, Shield, ShieldAlert, ShieldCheck, ShieldOff, Sparkles,
  Target, Trash2, Unlock
} from 'lucide-react-native';

export type IconName =
  | 'home' | 'apps' | 'web' | 'focus' | 'settings' | 'shield' | 'shieldOn' | 'shieldOff'
  | 'lock' | 'unlock' | 'clock' | 'eye' | 'eyeOff' | 'plus' | 'trash' | 'search' | 'ban'
  | 'sparkles' | 'play' | 'key' | 'list' | 'chevronRight';

export const Icons: Record<IconName, LucideIcon> = {
  home: Home,
  apps: Grid2x2,
  web: Globe,
  focus: Target,
  settings: SettingsIcon,
  shield: Shield,
  shieldOn: ShieldCheck,
  shieldOff: ShieldOff,
  lock: Lock,
  unlock: Unlock,
  clock: Clock,
  eye: Eye,
  eyeOff: EyeOff,
  plus: Plus,
  trash: Trash2,
  search: Search,
  ban: Ban,
  sparkles: Sparkles,
  play: Play,
  key: KeyRound,
  list: ListChecks,
  chevronRight: ChevronRight
};
