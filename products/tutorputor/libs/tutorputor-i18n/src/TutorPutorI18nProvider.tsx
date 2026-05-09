/**
 * TutorPutor I18n Provider
 *
 * Provides internationalization support for TutorPutor applications.
 * Manages locale catalogs and provides translation utilities.
 *
 * @doc.type component
 * @doc.purpose Internationalization provider for TutorPutor
 * @doc.layer platform
 * @doc.pattern Provider
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from "react";

export type Locale = "en" | "es" | "hi" | "zh";

export interface LocaleCatalog {
  code: Locale;
  name: string;
  nativeName: string;
  rtl: boolean;
  translations: Record<string, string>;
}

export interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
  availableLocales: Locale[];
  catalogs: Record<Locale, LocaleCatalog>;
}

const I18nContext = createContext<I18nContextValue | undefined>(undefined);

// Locale catalogs
const catalogs: Record<Locale, LocaleCatalog> = {
  en: {
    code: "en",
    name: "English",
    nativeName: "English",
    rtl: false,
    translations: {
      // Common
      "common.loading": "Loading...",
      "common.error": "Error",
      "common.success": "Success",
      "common.cancel": "Cancel",
      "common.save": "Save",
      "common.delete": "Delete",
      "common.edit": "Edit",
      "common.back": "Back",
      "common.next": "Next",
      "common.previous": "Previous",
      "common.submit": "Submit",
      "common.confirm": "Confirm",
      
      // Navigation
      "nav.home": "Home",
      "nav.dashboard": "Dashboard",
      "nav.learning": "Learning",
      "nav.simulations": "Simulations",
      "nav.assessments": "Assessments",
      "nav.settings": "Settings",
      "nav.profile": "Profile",
      "nav.logout": "Logout",
      
      // Auth
      "auth.login": "Login",
      "auth.register": "Register",
      "auth.email": "Email",
      "auth.password": "Password",
      "auth.forgotPassword": "Forgot Password?",
      "auth.resetPassword": "Reset Password",
      
      // Learning
      "learning.modules": "Modules",
      "learning.progress": "Progress",
      "learning.completed": "Completed",
      "learning.inProgress": "In Progress",
      "learning.notStarted": "Not Started",
      
      // Simulations
      "sim.title": "Simulation",
      "sim.start": "Start Simulation",
      "sim.restart": "Restart",
      "sim.results": "Results",
      "sim.score": "Score",
      
      // Assessments
      "assess.title": "Assessment",
      "assess.start": "Start Assessment",
      "assess.question": "Question",
      "assess.submit": "Submit Answer",
      "assess.results": "Results",
      
      // Errors
      "error.generic": "An error occurred. Please try again.",
      "error.network": "Network error. Please check your connection.",
      "error.unauthorized": "You are not authorized to perform this action.",
      "error.notFound": "The requested resource was not found.",
    },
  },
  es: {
    code: "es",
    name: "Spanish",
    nativeName: "Español",
    rtl: false,
    translations: {
      // Common
      "common.loading": "Cargando...",
      "common.error": "Error",
      "common.success": "Éxito",
      "common.cancel": "Cancelar",
      "common.save": "Guardar",
      "common.delete": "Eliminar",
      "common.edit": "Editar",
      "common.back": "Atrás",
      "common.next": "Siguiente",
      "common.previous": "Anterior",
      "common.submit": "Enviar",
      "common.confirm": "Confirmar",
      
      // Navigation
      "nav.home": "Inicio",
      "nav.dashboard": "Panel",
      "nav.learning": "Aprendizaje",
      "nav.simulations": "Simulaciones",
      "nav.assessments": "Evaluaciones",
      "nav.settings": "Configuración",
      "nav.profile": "Perfil",
      "nav.logout": "Cerrar sesión",
      
      // Auth
      "auth.login": "Iniciar sesión",
      "auth.register": "Registrarse",
      "auth.email": "Correo electrónico",
      "auth.password": "Contraseña",
      "auth.forgotPassword": "¿Olvidaste tu contraseña?",
      "auth.resetPassword": "Restablecer contraseña",
      
      // Learning
      "learning.modules": "Módulos",
      "learning.progress": "Progreso",
      "learning.completed": "Completado",
      "learning.inProgress": "En curso",
      "learning.notStarted": "No iniciado",
      
      // Simulations
      "sim.title": "Simulación",
      "sim.start": "Iniciar simulación",
      "sim.restart": "Reiniciar",
      "sim.results": "Resultados",
      "sim.score": "Puntuación",
      
      // Assessments
      "assess.title": "Evaluación",
      "assess.start": "Iniciar evaluación",
      "assess.question": "Pregunta",
      "assess.submit": "Enviar respuesta",
      "assess.results": "Resultados",
      
      // Errors
      "error.generic": "Ocurrió un error. Por favor, inténtalo de nuevo.",
      "error.network": "Error de red. Por favor, verifica tu conexión.",
      "error.unauthorized": "No estás autorizado para realizar esta acción.",
      "error.notFound": "El recurso solicitado no fue encontrado.",
    },
  },
  hi: {
    code: "hi",
    name: "Hindi",
    nativeName: "हिंदी",
    rtl: false,
    translations: {
      // Common
      "common.loading": "लोड हो रहा है...",
      "common.error": "त्रुटि",
      "common.success": "सफलता",
      "common.cancel": "रद्द करें",
      "common.save": "सहेजें",
      "common.delete": "हटाएं",
      "common.edit": "संपादित करें",
      "common.back": "वापस",
      "common.next": "अगला",
      "common.previous": "पिछला",
      "common.submit": "जमा करें",
      "common.confirm": "पुष्टि करें",
      
      // Navigation
      "nav.home": "होम",
      "nav.dashboard": "डैशबोर्ड",
      "nav.learning": "सीखना",
      "nav.simulations": "सिमुलेशन",
      "nav.assessments": "मूल्यांकन",
      "nav.settings": "सेटिंग्स",
      "nav.profile": "प्रोफाइल",
      "nav.logout": "लॉग आउट",
      
      // Auth
      "auth.login": "लॉग इन",
      "auth.register": "पंजीकरण",
      "auth.email": "ईमेल",
      "auth.password": "पासवर्ड",
      "auth.forgotPassword": "पासवर्ड भूल गए?",
      "auth.resetPassword": "पासवर्ड रीसेट करें",
      
      // Learning
      "learning.modules": "मॉड्यूल",
      "learning.progress": "प्रगति",
      "learning.completed": "पूर्ण",
      "learning.inProgress": "जारी",
      "learning.notStarted": "शुरू नहीं",
      
      // Simulations
      "sim.title": "सिमुलेशन",
      "sim.start": "सिमुलेशन शुरू करें",
      "sim.restart": "पुनः प्रारंभ करें",
      "sim.results": "परिणाम",
      "sim.score": "स्कोर",
      
      // Assessments
      "assess.title": "मूल्यांकन",
      "assess.start": "मूल्यांकन शुरू करें",
      "assess.question": "प्रश्न",
      "assess.submit": "उत्तर जमा करें",
      "assess.results": "परिणाम",
      
      // Errors
      "error.generic": "एक त्रुटि हुई। कृपया पुनः प्रयास करें।",
      "error.network": "नेटवर्क त्रुटि। कृपया अपना कनेक्शन जांचें।",
      "error.unauthorized": "आप इस कार्रवाई करने के लिए अधिकृत नहीं हैं।",
      "error.notFound": "अनुरोधित संसाधन नहीं मिला।",
    },
  },
  zh: {
    code: "zh",
    name: "Chinese",
    nativeName: "中文",
    rtl: false,
    translations: {
      // Common
      "common.loading": "加载中...",
      "common.error": "错误",
      "common.success": "成功",
      "common.cancel": "取消",
      "common.save": "保存",
      "common.delete": "删除",
      "common.edit": "编辑",
      "common.back": "返回",
      "common.next": "下一步",
      "common.previous": "上一步",
      "common.submit": "提交",
      "common.confirm": "确认",
      
      // Navigation
      "nav.home": "首页",
      "nav.dashboard": "仪表板",
      "nav.learning": "学习",
      "nav.simulations": "模拟",
      "nav.assessments": "评估",
      "nav.settings": "设置",
      "nav.profile": "个人资料",
      "nav.logout": "退出登录",
      
      // Auth
      "auth.login": "登录",
      "auth.register": "注册",
      "auth.email": "电子邮件",
      "auth.password": "密码",
      "auth.forgotPassword": "忘记密码？",
      "auth.resetPassword": "重置密码",
      
      // Learning
      "learning.modules": "模块",
      "learning.progress": "进度",
      "learning.completed": "已完成",
      "learning.inProgress": "进行中",
      "learning.notStarted": "未开始",
      
      // Simulations
      "sim.title": "模拟",
      "sim.start": "开始模拟",
      "sim.restart": "重新开始",
      "sim.results": "结果",
      "sim.score": "分数",
      
      // Assessments
      "assess.title": "评估",
      "assess.start": "开始评估",
      "assess.question": "问题",
      "assess.submit": "提交答案",
      "assess.results": "结果",
      
      // Errors
      "error.generic": "发生错误。请重试。",
      "error.network": "网络错误。请检查您的连接。",
      "error.unauthorized": "您无权执行此操作。",
      "error.notFound": "未找到请求的资源。",
    },
  },
};

export interface TutorPutorI18nProviderProps {
  children: ReactNode;
  defaultLocale?: Locale;
}

export function TutorPutorI18nProvider({
  children,
  defaultLocale = "en",
}: TutorPutorI18nProviderProps) {
  const [locale, setLocaleState] = useState<Locale>(defaultLocale);

  // Load saved locale from localStorage on mount
  useEffect(() => {
    const savedLocale = localStorage.getItem("tutorputor-locale") as Locale | null;
    if (savedLocale && catalogs[savedLocale]) {
      setLocaleState(savedLocale);
    }
  }, []);

  // Save locale to localStorage when it changes
  const setLocale = (newLocale: Locale) => {
    setLocaleState(newLocale);
    localStorage.setItem("tutorputor-locale", newLocale);
    document.documentElement.lang = newLocale;
    document.documentElement.dir = catalogs[newLocale].rtl ? "rtl" : "ltr";
  };

  // Set document language on mount and locale change
  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = catalogs[locale].rtl ? "rtl" : "ltr";
  }, [locale]);

  // Translation function
  const t = (key: string, params?: Record<string, string | number>): string => {
    let translation = catalogs[locale].translations[key];
    
    if (!translation) {
      // Fallback to English if translation not found
      translation = catalogs.en.translations[key];
    }
    
    if (!translation) {
      // Return key if no translation found
      return key;
    }
    
    // Replace parameters
    if (params) {
      Object.entries(params).forEach(([paramKey, value]) => {
        translation = translation.replace(`{{${paramKey}}}`, String(value));
      });
    }
    
    return translation;
  };

  const value: I18nContextValue = {
    locale,
    setLocale,
    t,
    availableLocales: Object.keys(catalogs) as Locale[],
    catalogs,
  };

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n must be used within TutorPutorI18nProvider");
  }
  return context;
}

// Helper function to get locale catalog
export function getLocaleCatalog(locale: Locale): LocaleCatalog {
  return catalogs[locale];
}

// Helper function to get all available locales
export function getAvailableLocales(): Locale[] {
  return Object.keys(catalogs) as Locale[];
}
