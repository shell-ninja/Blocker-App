import {
  ArrowDownToLine, Ban, ChevronRight, Clock, Compass, Eye, EyeOff, Film, Globe, Grid2x2, Home, KeyRound, Layers,
  ListChecks, Lock, LucideIcon, Play, Plus, Search, Settings as SettingsIcon, Shield, ShieldAlert,
  ShieldCheck, ShieldOff, Target, Trash2, Unlock
} from 'lucide-react-native';

export type IconName =
  | 'home' | 'apps' | 'web' | 'focus' | 'settings' | 'shield' | 'shieldOn' | 'shieldOff'
  | 'lock' | 'unlock' | 'clock' | 'eye' | 'eyeOff' | 'plus' | 'trash' | 'search' | 'ban'
  | 'download' | 'play' | 'key' | 'list' | 'chevronRight' | 'layers' | 'film' | 'guide';

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
  download: ArrowDownToLine,
  play: Play,
  key: KeyRound,
  list: ListChecks,
  chevronRight: ChevronRight,
  layers: Layers,
  film: Film,
  guide: Compass,
};
