import { useEffect, useState, useSyncExternalStore } from 'react';
import { getSnapshot, subscribe } from '../services/ProtectionManager';

export const useProtection = () => useSyncExternalStore(subscribe, getSnapshot);

export function useNow(ms = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const i = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(i);
  }, [ms]);
  return now;
}
