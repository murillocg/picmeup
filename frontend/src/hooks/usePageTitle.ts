import { useEffect } from 'react';

const BASE_TITLE = 'Elite Sport Photos';

export default function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} — ${BASE_TITLE}` : `${BASE_TITLE} — Find Your Event Photos`;
  }, [title]);
}
