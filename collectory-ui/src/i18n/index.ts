import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import HttpBackend from 'i18next-http-backend';

i18n
  .use(HttpBackend)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    supportedLngs: [
      'en', 'ca', 'da', 'de', 'de-AT', 'de-LU', 'es', 'eu', 'fr',
      'it', 'ja', 'nl', 'pt-BR', 'pt-PT', 'ru', 'sv', 'th', 'zh', 'zh-CN',
    ],
    keySeparator: false,
    nsSeparator: false,
    interpolation: {
      escapeValue: false, // React already escapes
    },
    backend: {
      loadPath: '/locales/{{lng}}/translation.json',
    },
  });

export default i18n;
