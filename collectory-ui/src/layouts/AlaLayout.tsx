import { Banner, Footer, Header, injectCommonInfo } from '@ala/common-ui';
import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import buildInfo from '../../package.json';

export function AlaLayout() {
  const { isAuthenticated, login, logout } = useAuth();
  const [cssLoaded, setCssLoaded] = useState(false);

  useEffect(() => {
    injectCommonInfo(
      buildInfo,
      import.meta.env.VITE_ENV,
      import.meta.env.VITE_COMMON_JS,
      import.meta.env.VITE_COMMON_CSS,
      setCssLoaded
    );
  }, []);

  // Don't render until common CSS is loaded (prevents FOUC)
  if (!cssLoaded) return null;

  return (
    <>
      <Header
        isLoggedIn={isAuthenticated}
        loginFn={login}
        logoutFn={logout}
        headerUrl={import.meta.env.VITE_COMMON_HEADER_HTML}
      />
      <Banner
        bannerUrl={import.meta.env.VITE_BANNER_MESSAGES_URL}
        scope={import.meta.env.VITE_BANNER_SCOPE}
      />
      {/* Breadcrumb portal slot — pages render <Breadcrumb> which portals here,
          placing it between header and main content (matching GSP ala-main.gsp layout) */}
      <div id="breadcrumb-slot" />
      <main className="container-fluid" id="content">
        <Outlet />
      </main>
      <Footer
        isLoggedIn={isAuthenticated}
        loginFn={login}
        logoutFn={logout}
        footerUrl={import.meta.env.VITE_COMMON_FOOTER_HTML}
      />
    </>
  );
}
