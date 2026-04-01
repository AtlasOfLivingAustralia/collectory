import { useEffect, useRef, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useConfig } from '../hooks/useConfig';
import { useAuth } from '../auth/useAuth';

const ENV_HEADER_URL = import.meta.env.VITE_COMMON_HEADER_HTML || '';
const ENV_FOOTER_URL = import.meta.env.VITE_COMMON_FOOTER_HTML || '';
const ENV_COMMON_CSS = import.meta.env.VITE_COMMON_CSS || '';
const ENV_COMMON_JS = import.meta.env.VITE_COMMON_JS || '';
const ENV_BANNER_URL = import.meta.env.VITE_BANNER_MESSAGES_URL || '';
const ENV_BANNER_SCOPE = import.meta.env.VITE_BANNER_SCOPE || '';

// --- Banner component (ported from @ala/common-ui) ---

enum BannerSeverity {
  INFO = 'INFO',
  WARNING = 'WARNING',
  DANGER = 'DANGER',
}

interface BannerMessages {
  [key: string]: {
    message: string;
    severity: BannerSeverity;
    updated: string;
    closable?: boolean;
  };
}

const BANNER_CLOSED_KEY = 'bannerLastClosed_';

function Banner({ bannerUrl, scope }: { bannerUrl: string; scope: string }) {
  const [messages, setMessages] = useState<BannerMessages>({});

  useEffect(() => {
    if (!bannerUrl) return;
    fetch(bannerUrl)
      .then((r) => r.json())
      .then((data: BannerMessages) => {
        const now = (iso: string) => new Date(iso).getTime();
        const lastClosed = (key: string) => {
          const v = localStorage.getItem(BANNER_CLOSED_KEY + key);
          return v ? new Date(v).getTime() : 0;
        };
        const filtered = Object.fromEntries(
          Object.entries(data).filter(
            ([key, val]) =>
              (key === 'global' || key === scope) &&
              val.message.length > 0 &&
              lastClosed(key) < now(val.updated)
          )
        );
        setMessages(filtered);
      })
      .catch(() => console.warn('Failed to load banner messages from', bannerUrl));
  }, [bannerUrl, scope]);

  function close(key: string) {
    localStorage.setItem(BANNER_CLOSED_KEY + key, new Date().toISOString());
    setMessages((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }

  if (!bannerUrl || Object.keys(messages).length === 0) return null;

  return (
    <>
      {Object.entries(messages).map(([key, val]) => (
        <div
          key={key}
          className={`alert alert-${val.severity.toLowerCase()}`}
          style={{ position: 'relative', display: 'flex', justifyContent: 'center', alignItems: 'center' }}
        >
          <div style={{ textAlign: 'center' }} dangerouslySetInnerHTML={{ __html: val.message }} />
          {val.closable && (
            <button
              onClick={() => close(key)}
              style={{ position: 'absolute', top: 0, right: 0, background: 'none', border: 'none', fontSize: '1.2em', margin: '0.8em' }}
              aria-label="Close"
              title="Dismiss message"
            >
              &times;
            </button>
          )}
        </div>
      ))}
    </>
  );
}

// --- DOM utility helpers (same pattern as @ala/common-ui) ---

function setElementDisplayByClassName(className: string, displayValue: string) {
  const elements = document.getElementsByClassName(className);
  for (let i = 0; i < elements.length; i++) {
    (elements[i] as HTMLElement).style.display = displayValue;
  }
}

function setElementDisplayById(id: string, displayValue: string) {
  const element = document.getElementById(id);
  if (element) {
    (element as HTMLElement).style.display = displayValue;
  }
}

function setClickEventByClassName(className: string, clickFn: (() => void) | undefined) {
  if (!clickFn) return;
  const elements = document.getElementsByClassName(className);
  for (let i = 0; i < elements.length; i++) {
    const el = elements[i];
    if (el) el.addEventListener('click', clickFn);
  }
}

function setClickEventById(id: string, clickFn: (() => void) | undefined) {
  if (!clickFn) return;
  const element = document.getElementById(id);
  if (element) {
    element.addEventListener('click', clickFn);
  }
}

function showLoginLogoutButtons(isLoggedIn: boolean | undefined) {
  setElementDisplayByClassName('signedIn', isLoggedIn ? 'inline-block' : 'none');
  setElementDisplayByClassName('signedOut', !isLoggedIn ? 'inline-block' : 'none');
}

// --- CSS injection (same pattern as @ala/common-ui injectCommonInfo) ---

function injectCommonCss(cssUrl: string, setCssLoaded: (loaded: boolean) => void) {
  if (!cssUrl) {
    setCssLoaded(true);
    return;
  }
  fetch(cssUrl)
    .then((response) => {
      if (response.ok) {
        response.text().then((text) => {
          const style = document.createElement('style');
          style.innerHTML = text;
          document.head.appendChild(style);
        });
      }
    })
    .finally(() => setCssLoaded(true));
}

function injectCommonJs(jsUrl: string) {
  if (!jsUrl) return;
  const script = document.createElement('script');
  script.src = jsUrl;
  script.async = true;
  document.body.appendChild(script);
}

// --- Header component ---

function AlaHeader({ headerUrl, isLoggedIn, loginFn, logoutFn }: {
  headerUrl: string;
  isLoggedIn?: boolean;
  loginFn?: () => void;
  logoutFn?: () => void;
}) {
  const headerRef = useRef<HTMLDivElement>(null);
  const [html, setHtml] = useState('');
  const [openMenu, setOpenMenu] = useState(0);
  const [searchVisible, setSearchVisible] = useState(false);
  const [menuVisible, setMenuVisible] = useState<boolean | undefined>(undefined);
  const [mobileWidth, setMobileWidth] = useState(10);

  // Fetch external header HTML
  useEffect(() => {
    if (headerUrl) {
      fetch(headerUrl)
        .then((r) => r.text())
        .then(setHtml)
        .catch(() => console.warn('Failed to load ALA header from', headerUrl));
    }
  }, [headerUrl]);

  // Inject HTML and set up DOM interactivity
  useEffect(() => {
    if (!headerRef.current || !html) return;
    headerRef.current.innerHTML = html;
    setupHeaderHtml();
  }, [html]);

  // React to login state changes
  useEffect(() => {
    showLoginLogoutButtons(isLoggedIn);
  }, [isLoggedIn]);

  function setupHeaderHtml() {
    const header = document.getElementsByTagName('header');
    if (header.length === 0) {
      setTimeout(setupHeaderHtml, 10);
      return;
    }

    const mWidthEl = document.getElementById('mobileMaxWidth') as HTMLInputElement | null;
    const mWidth = mWidthEl ? parseInt(mWidthEl.value, 10) : 768;
    setMobileWidth(mWidth);

    showLoginLogoutButtons(isLoggedIn);
    applySearchVisible();

    // Menu toggle (mobile)
    setClickEventByClassName('header-menu-button', () =>
      setMenuVisible((prev) => !prev),
    );

    // Search toggle
    setClickEventByClassName('header-search-button', () =>
      setSearchVisible((prev) => !prev),
    );

    // Dropdown menus
    const dropDownMenus = document.getElementsByClassName('dropdown-menu');
    for (let i = 1; i <= dropDownMenus.length; i++) {
      setClickEventById('header-open-menu-' + i, () =>
        setOpenMenu((prev) => (i === prev ? 0 : i)),
      );
    }

    // Login/logout buttons
    setClickEventByClassName('header-login-button', loginFn);
    setClickEventByClassName('header-logout-button', logoutFn);

    if (window.innerWidth > mWidth) {
      setMenuVisible(true);
    } else {
      setMenuVisible(false);
    }
  }

  function applySearchVisible() {
    setElementDisplayByClassName('search-visible-true', !searchVisible ? 'none' : 'inline-block');
    setElementDisplayByClassName('search-visible-false', searchVisible ? 'none' : 'inline-block');
  }

  // Open menu display
  useEffect(() => {
    const dropDownMenus = document.getElementsByClassName('dropdown-menu');
    for (let i = 1; i <= dropDownMenus.length; i++) {
      setElementDisplayById('header-open-menu-item-' + i, openMenu === i ? 'block' : 'none');
    }
  }, [openMenu]);

  // Search visibility
  useEffect(() => {
    applySearchVisible();
  }, [searchVisible]);

  // Mobile menu visibility
  useEffect(() => {
    setElementDisplayByClassName('collapse-menu-false', menuVisible ? 'none' : 'block');
    setElementDisplayByClassName('collapse-menu-true', !menuVisible ? 'none' : 'inline-block');
    setElementDisplayById('navbarOuterWrapper', menuVisible ? 'block' : 'none');
  }, [menuVisible]);

  // Window resize handler
  useEffect(() => {
    function handleResize() {
      if (window.innerWidth > mobileWidth) {
        setMenuVisible(true);
      } else {
        setMenuVisible(false);
      }
    }
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [mobileWidth]);

  // Close menu on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      const menuElement = document.getElementById('main-menu');
      if (menuElement && !menuElement.contains(event.target as Node)) {
        setOpenMenu(0);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  if (!headerUrl) return null;

  return <>{html && <div ref={headerRef} id="ala-header" />}</>;
}

// --- Footer component ---

function AlaFooter({ footerUrl, isLoggedIn, loginFn, logoutFn }: {
  footerUrl: string;
  isLoggedIn?: boolean;
  loginFn?: () => void;
  logoutFn?: () => void;
}) {
  const footerRef = useRef<HTMLDivElement>(null);
  const [html, setHtml] = useState('');

  // Fetch external footer HTML
  useEffect(() => {
    if (footerUrl) {
      fetch(footerUrl)
        .then((r) => r.text())
        .then(setHtml)
        .catch(() => console.warn('Failed to load ALA footer from', footerUrl));
    }
  }, [footerUrl]);

  // Inject HTML and set up DOM interactivity
  useEffect(() => {
    if (!footerRef.current || !html) return;
    footerRef.current.innerHTML = html;
    setupFooterHtml();
  }, [html]);

  // React to login state changes
  useEffect(() => {
    showLoginLogoutButtons(isLoggedIn);
  }, [isLoggedIn]);

  function setupFooterHtml() {
    const footer = document.getElementsByTagName('footer');
    if (footer.length === 0) {
      setTimeout(setupFooterHtml, 10);
      return;
    }
    showLoginLogoutButtons(isLoggedIn);
    setClickEventByClassName('footer-login-button', loginFn);
    setClickEventByClassName('footer-logout-button', logoutFn);
  }

  if (!footerUrl) return null;

  return <>{html && <div ref={footerRef} id="ala-footer" />}</>;
}

// --- Main layout ---

export function AlaLayout() {
  const { data: config } = useConfig();
  const { isAuthenticated, login, logout } = useAuth();
  const [cssLoaded, setCssLoaded] = useState(false);

  // Env vars take priority (they point to the new static-server HTML fragments).
  // Fall back to backend config base URL + /header.html if env vars are not set.
  const headerUrl = ENV_HEADER_URL
    || (config?.headerAndFooterBaseUrl ? `${config.headerAndFooterBaseUrl}/header.html` : '');
  const footerUrl = ENV_FOOTER_URL
    || (config?.headerAndFooterBaseUrl ? `${config.headerAndFooterBaseUrl}/footer.html` : '');

  // Inject common CSS (once)
  useEffect(() => {
    injectCommonCss(ENV_COMMON_CSS, setCssLoaded);
    injectCommonJs(ENV_COMMON_JS);
  }, []);

  // Don't render until common CSS is loaded (prevents FOUC)
  if (!cssLoaded) return null;

  return (
    <>
      <AlaHeader
        headerUrl={headerUrl}
        isLoggedIn={isAuthenticated}
        loginFn={login}
        logoutFn={logout}
      />
      <Banner bannerUrl={ENV_BANNER_URL} scope={ENV_BANNER_SCOPE} />
      {/* Breadcrumb portal slot — pages render <Breadcrumb> which portals here,
          placing it between header and main content (matching GSP ala-main.gsp layout) */}
      <div id="breadcrumb-slot" />
      <main className="container-fluid" id="content">
        <Outlet />
      </main>
      <AlaFooter
        footerUrl={footerUrl}
        isLoggedIn={isAuthenticated}
        loginFn={login}
        logoutFn={logout}
      />
    </>
  );
}
