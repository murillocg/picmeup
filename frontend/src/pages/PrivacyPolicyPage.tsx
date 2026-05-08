export default function PrivacyPolicyPage() {
  return (
    <div className="max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Privacy Policy</h1>
          <p className="text-gray-500 mt-1">Version 2.0 — Last updated April 2026</p>
        </div>
        <a
          href="/privacy-policy.pdf"
          download
          className="flex items-center gap-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 text-sm font-medium"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          Download PDF
        </a>
      </div>

      {/* Desktop: embedded PDF viewer */}
      <div className="hidden sm:block bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <object
          data="/privacy-policy.pdf"
          type="application/pdf"
          className="w-full"
          style={{ height: '80vh' }}
        >
          <div className="flex flex-col items-center justify-center py-16 text-gray-500 gap-4">
            <p>Your browser cannot display the PDF inline.</p>
            <a
              href="/privacy-policy.pdf"
              target="_blank"
              rel="noopener noreferrer"
              className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 text-sm font-medium"
            >
              Open PDF
            </a>
          </div>
        </object>
      </div>

      {/* Mobile: open/download buttons */}
      <div className="sm:hidden bg-white rounded-xl border border-gray-200 shadow-sm p-8 flex flex-col items-center gap-4 text-center">
        <svg className="w-12 h-12 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
        </svg>
        <p className="text-gray-600 text-sm">Tap below to read our Privacy Policy</p>
        <a
          href="/privacy-policy.pdf"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full bg-indigo-600 text-white px-4 py-3 rounded-lg hover:bg-indigo-700 text-sm font-medium text-center"
        >
          Open Privacy Policy
        </a>
        <a
          href="/privacy-policy.pdf"
          download
          className="w-full border border-gray-300 text-gray-700 px-4 py-3 rounded-lg hover:bg-gray-50 text-sm font-medium text-center"
        >
          Download PDF
        </a>
      </div>
    </div>
  );
}
