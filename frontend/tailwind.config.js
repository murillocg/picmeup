/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          orange: '#ee9b00',
          'orange-dark': '#d48a00',
          'orange-light': '#f5b840',
          charcoal: '#2f383b',
          'charcoal-light': '#3d494d',
        },
      },
    },
  },
  plugins: [],
}
