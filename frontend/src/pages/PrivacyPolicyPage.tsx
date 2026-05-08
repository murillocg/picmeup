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

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
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
    </div>
  );
}
