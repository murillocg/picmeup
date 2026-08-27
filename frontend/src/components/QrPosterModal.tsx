import { useEffect, useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import type { EventResponse } from '../types/api';
import { eventUrl } from '../config';

type Layout = 'type' | 'strip';
type Sheet = 'A4' | 'A3';

interface QrPosterModalProps {
  event: EventResponse;
  onClose: () => void;
}

export default function QrPosterModal({ event, onClose }: QrPosterModalProps) {
  const [layout, setLayout] = useState<Layout>('type');
  const [sheet, setSheet] = useState<Sheet>('A4');
  const qrRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const url = eventUrl(event.slug);
  const displayUrl = url.replace(/^https:\/\//, '');
  const date = new Date(event.date).toLocaleDateString('en-AU', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  // The QR must stay vector for print, so hand the print shop the SVG itself
  // rather than a screenshot of it.
  function downloadSvg() {
    const svg = qrRef.current?.querySelector('svg');
    if (!svg) return;

    const source = new XMLSerializer().serializeToString(svg);
    const blob = new Blob([source], { type: 'image/svg+xml' });
    const href = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = href;
    link.download = `${event.slug}-qr.svg`;
    link.click();
    URL.revokeObjectURL(href);
  }

  const poster = (
    <div className={`qr-poster ${layout === 'strip' ? 'qr-poster--strip' : 'qr-poster--sheet'}`}>
      {layout === 'strip' ? (
        <>
          <div className="qr-poster__qr" ref={qrRef}>
            <QRCodeSVG value={url} level="H" marginSize={2} size={512} />
          </div>
          <div>
            <img src="/logo.png" alt="Elite Sport Photos" className="qr-poster__logo" />
            <div className="qr-poster__kicker">Photos from today</div>
            <div className="qr-poster__headline">
              Find your<span>self</span>
            </div>
            <div className="qr-poster__meta">
              {event.name} &middot; {date}
            </div>
          </div>
        </>
      ) : (
        <>
          <div>
            <img src="/logo.png" alt="Elite Sport Photos" className="qr-poster__logo" />
            <div className="qr-poster__kicker">Photos from today</div>
            <div className="qr-poster__headline">
              FIND
              <br />
              YOUR<span>SELF</span>
            </div>
          </div>

          <div className="qr-poster__qr" ref={qrRef}>
            <QRCodeSVG value={url} level="H" marginSize={2} size={512} />
          </div>

          <div>
            <div className="qr-poster__name">{event.name}</div>
            <div className="qr-poster__meta">
              {event.location} &middot; {date}
            </div>
            <div className="qr-poster__url">{displayUrl}</div>
          </div>
        </>
      )}
    </div>
  );

  return (
    <div
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      {/* Drives the printer's paper size and orientation from the chosen layout. */}
      <style>{`@page { size: ${sheet} ${layout === 'strip' ? 'landscape' : 'portrait'}; margin: 0; }`}</style>

      <div
        className="bg-white rounded-lg max-w-md w-full max-h-full overflow-y-auto p-5"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Print QR poster</h2>

        <div className="flex gap-2 mb-3">
          <ChoiceButton active={layout === 'type'} onClick={() => setLayout('type')}>
            Poster
          </ChoiceButton>
          <ChoiceButton active={layout === 'strip'} onClick={() => setLayout('strip')}>
            Barrier strip
          </ChoiceButton>
        </div>

        <div className="qr-print-sheet border border-gray-200 rounded overflow-hidden mb-3">
          {poster}
        </div>

        <div className="flex gap-2 mb-4">
          {(['A4', 'A3'] as const).map((option) => (
            <ChoiceButton key={option} active={sheet === option} onClick={() => setSheet(option)}>
              {option}
            </ChoiceButton>
          ))}
        </div>

        <p className="text-xs text-gray-500 mb-4">
          Links to <span className="font-mono">{displayUrl}</span>. A code scans from about ten
          times its own width, so an A4 poster works from roughly 1.2&nbsp;m away.
        </p>

        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => window.print()}
            className="flex-1 bg-brand-orange text-white px-4 py-2 rounded-lg hover:bg-brand-orange-dark text-sm font-medium"
          >
            Print
          </button>
          <button
            onClick={downloadSvg}
            className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-sm"
          >
            Download QR
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-sm"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

function ChoiceButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1.5 rounded-full text-sm border ${
        active
          ? 'border-brand-orange text-brand-orange-dark bg-orange-50 font-medium'
          : 'border-gray-300 text-gray-600 hover:bg-gray-50'
      }`}
    >
      {children}
    </button>
  );
}
