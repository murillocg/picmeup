export type MobilePlatform = 'ios' | 'android';

export function getMobilePlatform(): MobilePlatform | null {
  if (typeof navigator === 'undefined') return null;

  const ua = navigator.userAgent;

  // iPadOS reports itself as a Mac, so touch points are what set it apart from a desktop.
  if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) {
    return 'ios';
  }
  if (/Android/.test(ua)) {
    return 'android';
  }
  return null;
}
