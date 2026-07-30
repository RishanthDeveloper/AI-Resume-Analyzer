// Configuration file for AI Resume Analyzer frontend
// Automatically detects environment (localhost vs production)

(function () {
  const isLocalhost = Boolean(
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1" ||
    window.location.hostname === "[::1]"
  );

  const defaultLocalApi = "http://localhost:8000/api/analyze";
  const defaultProdApi = "https://resume-analyzer-backend.onrender.com/api/analyze";

  const apiUrl = window.ENV_API_URL || (isLocalhost ? defaultLocalApi : defaultProdApi);
  const marketUrl = apiUrl.replace("/analyze", "/market-trends");

  window.APP_CONFIG = {
    API_URL: apiUrl,
    MARKET_TRENDS_URL: window.ENV_MARKET_TRENDS_URL || marketUrl,

    // Supabase Project Credentials (safe for client exposure)
    SUPABASE_URL: window.ENV_SUPABASE_URL || "https://your-supabase-project.supabase.co",
    SUPABASE_ANON_KEY: window.ENV_SUPABASE_ANON_KEY || "your-supabase-anon-key-here"
  };
})();
