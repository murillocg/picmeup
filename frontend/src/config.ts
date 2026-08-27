// The public origin, used wherever a link has to survive outside the browser that
// built it — printed QR posters, JSON-LD. Deliberately not window.location.origin:
// a poster generated from a dev machine would otherwise encode localhost, and the
// mistake is only discovered after it is printed.
export const SITE_URL = 'https://elitesportphotos.com';

export function eventUrl(slug: string): string {
  return `${SITE_URL}/events/${slug}`;
}
