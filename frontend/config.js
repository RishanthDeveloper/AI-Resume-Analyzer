// Configuration file for AI Resume Analyzer frontend
// Automatically detects environment (localhost vs production)

(function () {
  const isLocalhost = Boolean(
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1" ||
    window.location.hostname === "[::1]"
  );

  const defaultLocalApi = "http://localhost:8000/api/analyze";
  // Production fallback API endpoint (Render backend)
  const defaultProdApi = "https://resume-analyzer-backend.onrender.com/api/analyze";

  window.APP_CONFIG = {
    // Dynamic API URL resolution
    API_URL: window.ENV_API_URL || (isLocalhost ? defaultLocalApi : defaultProdApi),

    // Supabase Project Credentials (safe for client exposure)
    SUPABASE_URL: window.ENV_SUPABASE_URL || "https://your-supabase-project.supabase.co",
    SUPABASE_ANON_KEY: window.ENV_SUPABASE_ANON_KEY || "your-supabase-anon-key-here"
  };
})();
