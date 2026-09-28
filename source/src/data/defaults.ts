import { USER_DOMAINS, USER_KEYWORDS, USER_TLDS } from './userLists';

export interface Category {
  id: string;
  title: string;
  description: string;
  defaultOn: boolean;
  domains: string[];
  keywords: string[];
  tlds?: string[];
}

export const CATEGORIES: Category[] = [
  {
    id: 'default_list',
    title: 'Default block list',
    description: `${USER_DOMAINS.length} sites, ${USER_KEYWORDS.length} keywords and ${USER_TLDS.length} web endings, updated with your own additions`,
    defaultOn: true,
    domains: USER_DOMAINS,
    keywords: USER_KEYWORDS,
    tlds: USER_TLDS
  },
  {
    id: 'gambling',
    title: 'Gambling',
    description: 'Betting, casino and poker sites (optional extra, off by default)',
    defaultOn: false,
    keywords: ['sportsbook', 'online casino', 'slot machine', 'betting odds'],
    domains: [
      'bet365.com', 'stake.com', '1xbet.com', 'pokerstars.com', 'draftkings.com', 'fanduel.com', 'betway.com',
      'bovada.lv', 'betfair.com', 'williamhill.com', 'paddypower.com', 'unibet.com', '888casino.com',
      'bwin.com', 'ladbrokes.com', 'betmgm.com', 'caesars.com', 'partypoker.com', 'gginpoker.com', 'mostbet.com',
      'parimatch.com', 'melbet.com', 'linebet.com', 'betwinner.com', '22bet.com', 'roobet.com', 'bc.game'
    ]
  }
];
