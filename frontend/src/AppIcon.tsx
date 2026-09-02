import type { ReactNode } from 'react';

export type AppIconName =
  | 'bench' | 'book' | 'bookmark' | 'broom' | 'building' | 'camera' | 'check'
  | 'clock' | 'dots' | 'drop' | 'dumbbell' | 'food' | 'form' | 'gift' | 'info'
  | 'leaf' | 'list' | 'mail' | 'phone' | 'pin' | 'plus' | 'road' | 'share' | 'shield'
  | 'star' | 'tap' | 'trash' | 'users' | 'bulb';

const drawings: Record<AppIconName, ReactNode> = {
  bench: <><path d="M5 11h14M6 11v4h12v-4M7 15v5M17 15v5M8 8h8" /></>,
  book: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v16H6.5A2.5 2.5 0 0 0 4 21.5z" /><path d="M20 5.5A2.5 2.5 0 0 0 17.5 3H13v16h4.5a2.5 2.5  0 0 1 2.5 2.5z" /></>,
  bookmark: <path d="M6 3h12v18l-6-4-6 4z" />,
  broom: <><path d="m14 4 6 6M16.5 6.5 10 13" /><path d="M10 13c-2 0-4.5 1.5-5.5 6.5 5 .5 8.5-1 9.5-4z" /></>,
  building: <><path d="M4 21V7l8-4 8 4v14M8 10h2M14 10h2M8 14h2M14 14h2M10 21v-4h4v4" /></>,
  camera: <><path d="M4 7h3l1.5-2h7L17 7h3v12H4z" /><circle cx="12" cy="13" r="3.5" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  dots: <><circle cx="5" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none" /></>,
  drop: <path d="M12 3s6 6.3 6 11a6 6 0 0 1-12 0c0-4.7 6-11 6-11z" />,
  dumbbell: <><path d="M6 9v6M3.5 10v4M18 9v6M20.5 10v4M6 12h12" /></>,
  food: <><path d="M6 3v7M3.5 3v4A2.5 2.5 0 0 0 6 9.5 2.5 2.5 0 0 0 8.5 7V3M6 10v11M16 3v18M16 3c3 2 4 5 4 8h-4" /></>,
  form: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 8h8M8 12h8M8 16h5" /></>,
  gift: <><rect x="3" y="9" width="18" height="12" rx="1" /><path d="M12 9v12M3 13h18M12 9H8.5A2.5 2.5 0 1 1 11 6.5zM12 9h3.5A2.5 2.5 0 1 0 13 6.5z" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7h.01" /></>,
  leaf: <><path d="M20 4C11 4 5 8 5 15c0 3 2 5 5 5 7 0 10-7 10-16z" /><path d="M4 21c3-6 7-9 12-12" /></>,
  list: <><path d="M9 6h11M9 12h11M9 18h11" /><path d="M4 6h.01M4 12h.01M4 18h.01" /></>,
  mail: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m4 7 8 6 8-6" /></>,
  phone: <path d="M7.2 3.5 10 8 8.2 9.8a15 15 0 0 0 6 6l1.8-1.8 4.5 2.8-.8 3.2c-.2.8-1 1.3-1.8 1.2C10 20.2 3.8 14 2.8 6.1c-.1-.8.4-1.6 1.2-1.8z" />,
  pin: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0z" /><circle cx="12" cy="10" r="2.5" /></>,
  plus: <path d="M12 5v14M5 12h14" />,
  road: <><path d="M8 3 5 21M16 3l3 18M12 4v3M12 11v3M12 18v2" /></>,
  share: <><circle cx="18" cy="5" r="2.5" /><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="19" r="2.5" /><path d="m8.2 10.8 7.6-4.4M8.2 13.2l7.6 4.4" /></>,
  shield: <><path d="M12 3 20 6v5c0 5-3.4 8.3-8 10-4.6-1.7-8-5-8-10V6z" /><path d="m9 12 2 2 4-4" /></>,
  star: <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z" />,
  tap: <><path d="M7 8V5h7M11 5V3h4v2M5 8h14v5h-4c0 3-1.5 5-4 5s-4-2-4-5H5z" /><path d="M11 21h.01" /></>,
  trash: <><path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14M10 11v6M14 11v6" /></>,
  users: <><circle cx="9" cy="8" r="3" /><path d="M3.5 20c.5-4 2.3-6 5.5-6s5 2 5.5 6M16 5.5a3 3 0 0 1 0 5.5M16 14c2.7.2 4.2 2.2 4.5 5" /></>,
  bulb: <><path d="M9 18h6M10 21h4M8.5 15.5A7 7 0 1 1 15.5 15.5L15 17H9z" /><path d="M12 2V1M4.5 5.5l-1-1M19.5 5.5l1-1" /></>,
};

type Props = { name: AppIconName; className?: string };

export function AppIcon({ name, className = '' }: Props) {
  return <svg className={`app-icon ${className}`.trim()} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{drawings[name]}</svg>;
}
