import { Link } from 'react-router-dom';

const faqs = [
  {
    question: 'How does the photo search work?',
    answer:
      'We use AI-powered facial recognition to help you quickly find your photos from an event. Simply upload a selfie, and our system will scan the event gallery to match images that look like you.',
  },
  {
    question: 'Do you store my selfie?',
    answer:
      'No. Your selfie is processed in real-time to perform the search and is immediately and permanently deleted from our systems upon completion — whether or not a match is found. We do not retain any biometric data derived from your selfie.',
  },
  {
    question: 'Are my photos publicly available?',
    answer:
      'No. Event images are not publicly accessible. Our galleries are private, and access is restricted to individuals searching for their own photos.',
  },
  {
    question: 'How long are event photos stored?',
    answer:
      'All event images are securely stored for a limited time and are permanently deleted from our secure private servers within 90 days after the event concludes.',
  },
  {
    question: 'Can someone else find my photos using their selfie?',
    answer:
      'Our system returns results based on facial similarity, meaning users primarily see images of themselves. While no system is perfect, we take steps to minimise incorrect matches and protect your privacy.',
  },
  {
    question: "What if I can't find my photos?",
    answer:
      "While we do our absolute best to capture every single athlete at events, unfortunately sometimes this is just not possible. If you have tried taking a selfie up to three times and still have no luck, chances are we have missed you and we sincerely apologise.",
  },
  {
    question: 'How do I purchase my photos?',
    answer:
      "Once you've found your images, you can easily purchase and download them directly through our platform via a PayPal checkout using either your PayPal account or credit card.",
  },
  {
    question: 'Where are my images downloaded?',
    answer:
      'On a laptop or desktop: after checkout you will be prompted to select a folder to save the zip file (for multiple images) or individual photos.\n\nOn iPhone/iPad: your images will be saved in the Files app. If you purchased all images, open the zip file first — all images will then save to Files.\n\nOn Android: your images will be saved to your Downloads folder. If you purchased all images, open the zip file to extract them.',
  },
  {
    question: 'Can I print or share my purchased photos?',
    answer:
      'Yes. Once purchased, photos are yours for personal use — including printing, framing, and sharing on social media. For any questions about usage rights, please refer to our Privacy Policy or contact us.',
  },
  {
    question: 'How do I contact you?',
    answer:
      'You can reach our team by email at trent@elitesportphotos.com. We aim to respond to all enquiries within 30 days.',
  },
];

export default function FaqPage() {
  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-2">Frequently Asked Questions</h1>
      <p className="text-gray-500 mb-8">
        Everything you need to know about finding and purchasing your event photos. For full
        details on how we handle your data, see our{' '}
        <Link to="/privacy-policy" className="text-indigo-600 hover:text-indigo-800 underline">
          Privacy Policy
        </Link>
        .
      </p>

      <div className="space-y-4">
        {faqs.map((faq) => (
          <div key={faq.question} className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-2">{faq.question}</h2>
            <p className="text-gray-600 leading-relaxed whitespace-pre-line">{faq.answer}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
