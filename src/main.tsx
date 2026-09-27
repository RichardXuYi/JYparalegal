/**
 * React Application Entry Point
 */
// Must be first: injects window.electron / window['grandpoem-studio'] shims
// before any module reads them.
import './lib/web-host-bridge';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from './App';
import './i18n';
import './styles/globals.css';
import './styles/mobile.css';
import 'katex/dist/katex.min.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>,
);
