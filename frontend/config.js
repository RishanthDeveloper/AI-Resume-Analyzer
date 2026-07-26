// Configuration file for AI Resume Analyzer frontend
// Easy location to swap environment endpoints for production (e.g. Render backend & Supabase)

window.APP_CONFIG = {
  // Backend Spring Boot API endpoint
  // Replace with your Render URL in production (e.g. "https://resume-analyzer-backend.onrender.com/api/analyze")
  API_URL: "http://localhost:8000/api/analyze",

  // Supabase Project Credentials (Anon/Public key is safe for client browser exposure)
  SUPABASE_URL: "https://your-supabase-project.supabase.co",
  SUPABASE_ANON_KEY: "your-supabase-anon-key-here"
};
