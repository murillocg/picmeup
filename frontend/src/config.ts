// The public origin, used wherever a link has to survive outside the browser that
// built it — printed QR posters, JSON-LD. Deliberately not window.location.origin:
// a poster generated from a dev machine would otherwise encode localhost, and the
// mistake is only discovered after it is printed.
// Must be the www host: the apex redirects only "/" and serves a bare 404 on
// every deeper path, so an apex event link is dead. A printed poster cannot be
// corrected after the fact, so this constant has to be the host that answers.
export const SITE_URL = 'https://www.elitesportphotos.com';

export function eventUrl(slug: string): string {
  return `${SITE_URL}/events/${slug}`;
}
