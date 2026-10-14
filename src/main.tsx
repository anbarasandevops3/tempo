import { render } from 'preact';
import { App } from './ui/App';
import './styles.css';

render(<App />, document.getElementById('app')!);

// Offline support. Service workers need https (or localhost), so this is a no-op on a LAN dev server.
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  navigator.serviceWorker.register('./sw.js').catch(() => {});
}
