/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Auth / API
  readonly VITE_APP_API_URL: string;
  readonly VITE_APP_BASE_URL: string;

  // Collectory API base (for /ws/* calls — same as VITE_APP_API_URL in most envs)
  readonly VITE_API_BASE_URL: string;

  // ALA Common UI static fragments
  readonly VITE_COMMON_HEADER_HTML: string;
  readonly VITE_COMMON_FOOTER_HTML: string;
  readonly VITE_COMMON_CSS: string;
  readonly VITE_COMMON_JS: string;

  // Banner
  readonly VITE_BANNER_MESSAGES_URL: string;
  readonly VITE_BANNER_SCOPE: string;

  // Environment tag displayed in header meta
  readonly VITE_ENV: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
