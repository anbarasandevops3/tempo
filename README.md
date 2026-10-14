# Tempo

A personal interval timer for interval running, HIIT and warm-ups. It runs in the browser and is
meant to be added to an iPhone home screen.

The whole workout, voice and beeps included, is rendered into one audio track before it starts, so
the timer keeps going with the screen locked. See [docs/adr/001-rendered-audio-track.md](docs/adr/001-rendered-audio-track.md).

**Use it:** open the GitHub Pages URL in Safari, then Share → Add to Home Screen.

```bash
npm install
npm run dev     # dev server
npm test        # unit tests
npm run build   # production build in dist/
npm run deploy  # test, build and publish dist/ to the gh-pages branch
```
