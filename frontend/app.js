// app.js — AI Resume Analyzer Frontend Logic with Supabase Auth, Multi-Section Placement Intelligence & Live Market Skill Radar

// Read configuration from window.APP_CONFIG (frontend/config.js)
const CONFIG = window.APP_CONFIG || {
  API_URL: "http://localhost:8000/api/analyze",
  MARKET_TRENDS_URL: "http://localhost:8000/api/market-trends",
  SUPABASE_URL: "",
  SUPABASE_ANON_KEY: ""
};

// --- State Management ---
const state = {
  apiKey: localStorage.getItem("gemini_api_key") || "",
  jobDescription: "",
  marketRole: "",
  file: null,
  isLoading: false,
  isMarketLoading: false,
  currentUser: null,
  activeAuthTab: "signin", // "signin" | "signup"
  supabase: null
};

// --- DOM References ---
const apiKeyInput = document.getElementById("apiKeyInput");
const toggleVisibility = document.getElementById("toggleVisibility");
const jdInput = document.getElementById("jdInput");
const jdCharCount = document.getElementById("jdCharCount");
const loadExampleBtn = document.getElementById("loadExampleBtn");
const dropzone = document.getElementById("dropzone");
const dropzoneText = document.getElementById("dropzoneText");
const browseBtn = document.getElementById("browseBtn");
const fileInput = document.getElementById("fileInput");
const analyzeBtn = document.getElementById("analyzeBtn");
const analyzeBtnLabel = document.getElementById("analyzeBtnLabel");
const errorText = document.getElementById("errorText");

// Live Market Radar References
const marketRoleInput = document.getElementById("marketRoleInput");
const scanMarketBtn = document.getElementById("scanMarketBtn");
const scanMarketBtnLabel = document.getElementById("scanMarketBtnLabel");
const marketErrorText = document.getElementById("marketErrorText");
const marketResultsContainer = document.getElementById("marketResultsContainer");
const marketRoleBadge = document.getElementById("marketRoleBadge");
const totalPostingsBadge = document.getElementById("totalPostingsBadge");
const trendingSkillsList = document.getElementById("trendingSkillsList");
const missingMarketSkillsBadges = document.getElementById("missingMarketSkillsBadges");
const samplePostingsGrid = document.getElementById("samplePostingsGrid");

// Auth & Modal References
const openAuthModalBtn = document.getElementById("openAuthModalBtn");
const closeAuthModalBtn = document.getElementById("closeAuthModalBtn");
const authModal = document.getElementById("authModal");
const authNavContainer = document.getElementById("authNavContainer");
const historyNavBtn = document.getElementById("historyNavBtn");
const tabSignin = document.getElementById("tabSignin");
const tabSignup = document.getElementById("tabSignup");
const authForm = document.getElementById("authForm");
const authEmail = document.getElementById("authEmail");
const authPassword = document.getElementById("authPassword");
const authSubmitBtn = document.getElementById("authSubmitBtn");
const authError = document.getElementById("authError");
const authSuccess = document.getElementById("authSuccess");

// History Modal References
const historyModal = document.getElementById("historyModal");
const closeHistoryModalBtn = document.getElementById("closeHistoryModalBtn");
const historyListContainer = document.getElementById("historyListContainer");

// Results Dashboard References
const resultsContainer = document.getElementById("resultsContainer");
const historySaveBadge = document.getElementById("historySaveBadge");

// Section 1: ATS Score
const atsScoreBadge = document.getElementById("atsScoreBadge");
const scoreFormatVal = document.getElementById("scoreFormatVal");
const scoreFormatBar = document.getElementById("scoreFormatBar");
const scoreKeywordVal = document.getElementById("scoreKeywordVal");
const scoreKeywordBar = document.getElementById("scoreKeywordBar");
const scoreSectionVal = document.getElementById("scoreSectionVal");
const scoreSectionBar = document.getElementById("scoreSectionBar");
const atsSummary = document.getElementById("atsSummary");

// Section 4: Job Matching
const jobMatchBadge = document.getElementById("jobMatchBadge");
const jobMatchReasoning = document.getElementById("jobMatchReasoning");
const keyStrengthsList = document.getElementById("keyStrengthsList");
const gapsList = document.getElementById("gapsList");

// Section 2: Skill Gap Analysis
const skillGapSummary = document.getElementById("skillGapSummary");
const missingSkillsBadges = document.getElementById("missingSkillsBadges");
const matchingSkillsBadges = document.getElementById("matchingSkillsBadges");

// Section 3: Resume Suggestions
const lineRewritesTable = document.getElementById("lineRewritesTable");
const generalAdviceList = document.getElementById("generalAdviceList");

// Section 5: Interview Questions
const interviewQuestionsContainer = document.getElementById("interviewQuestionsContainer");

// --- Initialize Supabase Client ---
function initSupabase() {
  if (CONFIG.SUPABASE_URL && CONFIG.SUPABASE_ANON_KEY && !CONFIG.SUPABASE_URL.includes("your-supabase-project")) {
    try {
      if (window.supabase) {
        state.supabase = window.supabase.createClient(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY);
        checkCurrentSession();
      }
    } catch (err) {
      console.warn("[Supabase] Failed to initialize Supabase client:", err);
    }
  }
}

async function checkCurrentSession() {
  if (!state.supabase) return;
  try {
    const { data: { session } } = await state.supabase.auth.getSession();
    if (session?.user) {
      state.currentUser = session.user;
      renderAuthNavState();
    }
    
    state.supabase.auth.onAuthStateChange((_event, session) => {
      state.currentUser = session?.user || null;
      renderAuthNavState();
    });
  } catch (err) {
    console.warn("[Supabase] Session check failed:", err);
  }
}

function renderAuthNavState() {
  if (state.currentUser) {
    historyNavBtn?.classList.remove("hidden");
    if (authNavContainer) {
      authNavContainer.innerHTML = `
        <div class="flex items-center gap-3">
          <span class="text-xs text-slate-300 font-medium truncate max-w-[150px]">👤 ${state.currentUser.email}</span>
          <button id="signOutBtn" type="button" class="text-xs font-semibold text-rose-400 border border-rose-500/30 hover:bg-rose-500/10 px-3 py-1.5 rounded-lg transition">
            Sign Out
          </button>
        </div>
      `;
      document.getElementById("signOutBtn")?.addEventListener("click", handleSignOut);
    }
  } else {
    historyNavBtn?.classList.add("hidden");
    if (authNavContainer) {
      authNavContainer.innerHTML = `
        <button id="openAuthModalBtn" type="button"
          class="text-xs font-semibold text-slate-900 bg-emerald-400 hover:bg-emerald-300 px-4 py-2 rounded-lg transition shadow-md shadow-emerald-400/20 flex items-center gap-1.5">
          <span>🔑</span> Sign In / Sign Up
        </button>
      `;
      document.getElementById("openAuthModalBtn")?.addEventListener("click", () => showAuthModal());
    }
  }
}

// --- Helpers ---
function refreshAnalyzeButton() {
  const ready = state.apiKey.trim().length > 0 && state.jobDescription.trim().length > 0 && !!state.file;
  if (analyzeBtn) analyzeBtn.disabled = !ready || state.isLoading;
}

function refreshMarketScanButton() {
  const ready = state.marketRole.trim().length > 0 && !!state.file;
  if (scanMarketBtn) scanMarketBtn.disabled = !ready || state.isMarketLoading;
}

function showError(message) {
  if (!errorText) return;
  if (!message) {
    errorText.classList.add("hidden");
    errorText.textContent = "";
    return;
  }
  errorText.textContent = message;
  errorText.classList.remove("hidden");
}

function showMarketError(message) {
  if (!marketErrorText) return;
  if (!message) {
    marketErrorText.classList.add("hidden");
    marketErrorText.textContent = "";
    return;
  }
  marketErrorText.textContent = message;
  marketErrorText.classList.remove("hidden");
}

function setLoading(isLoading) {
  state.isLoading = isLoading;
  if (analyzeBtn) analyzeBtn.classList.toggle("is-loading", isLoading);
  if (analyzeBtnLabel) {
    analyzeBtnLabel.innerHTML = isLoading
      ? '<span class="spinner"></span>Analyzing Resume &amp; Generating Interview Q&amp;A...'
      : "✨ Analyze Resume &amp; Generate Interview Prep 🚀";
  }
  refreshAnalyzeButton();
}

function setMarketLoading(isLoading) {
  state.isMarketLoading = isLoading;
  if (scanMarketBtn) scanMarketBtn.classList.toggle("is-loading", isLoading);
  if (scanMarketBtnLabel) {
    scanMarketBtnLabel.innerHTML = isLoading
      ? '<span class="spinner"></span>Scanning Live Remotive Market Jobs...'
      : "📡 Scan Live Market";
  }
  refreshMarketScanButton();
}

// --- API Key Setup ---
if (state.apiKey && apiKeyInput) {
  apiKeyInput.value = state.apiKey;
  refreshAnalyzeButton();
}

apiKeyInput?.addEventListener("input", (e) => {
  state.apiKey = e.target.value;
  localStorage.setItem("gemini_api_key", state.apiKey);
  refreshAnalyzeButton();
});

toggleVisibility?.addEventListener("click", () => {
  if (apiKeyInput) {
    apiKeyInput.type = apiKeyInput.type === "password" ? "text" : "password";
  }
});

// --- Job Description Field & Example ---
jdInput?.addEventListener("input", (e) => {
  state.jobDescription = e.target.value;
  if (jdCharCount) jdCharCount.textContent = `${e.target.value.length} chars`;
  refreshAnalyzeButton();
});

loadExampleBtn?.addEventListener("click", () => {
  const example =
    "Software Development Engineer (Placement Role) — Seeking a Java Backend Developer. " +
    "Requirements: Strong proficiency in Java 17, Spring Boot 3, REST API architecture, and SQL database design. " +
    "Responsibilities: Design scalable microservices, write clean maintainable code, write unit tests with JUnit/Mockito, " +
    "and collaborate with frontend teams. Exposure to Docker, Redis, and Cloud platforms (AWS/Render) is a strong plus.";
  if (jdInput) {
    jdInput.value = example;
    jdInput.dispatchEvent(new Event("input"));
  }
});

// --- Market Role Input ---
marketRoleInput?.addEventListener("input", (e) => {
  state.marketRole = e.target.value;
  refreshMarketScanButton();
});

// --- File Handling ---
function handleFile(file) {
  if (!file) return;
  if (file.type !== "application/pdf") {
    showError("Only PDF files are supported.");
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    showError("File exceeds the 10MB limit.");
    return;
  }
  showError(null);
  showMarketError(null);
  state.file = file;
  if (dropzoneText) {
    dropzoneText.innerHTML = `📄 <strong class="text-emerald-400">${file.name}</strong> selected (${(file.size / 1024 / 1024).toFixed(2)} MB)`;
  }
  refreshAnalyzeButton();
  refreshMarketScanButton();
}

browseBtn?.addEventListener("click", (e) => {
  e.stopPropagation();
  fileInput?.click();
});

dropzone?.addEventListener("click", (e) => {
  if (e.target === browseBtn) return;
  fileInput?.click();
});

fileInput?.addEventListener("change", (e) => handleFile(e.target.files[0]));

// Drag and drop events
["dragenter", "dragover"].forEach((evt) => {
  dropzone?.addEventListener(evt, (e) => {
    e.preventDefault();
    e.stopPropagation();
    dropzone.classList.add("dropzone--active");
  });
});

["dragleave", "dragend"].forEach((evt) => {
  dropzone?.addEventListener(evt, (e) => {
    e.preventDefault();
    e.stopPropagation();
    dropzone.classList.remove("dropzone--active");
  });
});

dropzone?.addEventListener("drop", (e) => {
  e.preventDefault();
  e.stopPropagation();
  dropzone.classList.remove("dropzone--active");
  handleFile(e.dataTransfer?.files?.[0]);
});

window.addEventListener("dragover", (e) => e.preventDefault());
window.addEventListener("drop", (e) => e.preventDefault());

// --- Analyze Request Action ---
analyzeBtn?.addEventListener("click", async () => {
  showError(null);
  resultsContainer?.classList.add("hidden");
  historySaveBadge?.classList.add("hidden");
  setLoading(true);

  try {
    const formData = new FormData();
    formData.append("resume", state.file);
    formData.append("jobDescription", state.jobDescription);
    formData.append("apiKey", state.apiKey);

    const headers = {};
    if (state.supabase) {
      const { data: { session } } = await state.supabase.auth.getSession();
      if (session?.access_token) {
        headers["Authorization"] = `Bearer ${session.access_token}`;
      }
    }

    const response = await fetch(CONFIG.API_URL, {
      method: "POST",
      headers: headers,
      body: formData,
    });

    if (!response.ok) {
      let message = `Request failed with status ${response.status}`;
      try {
        const errorBody = await response.json();
        message = errorBody.message || message;
      } catch (_) {}
      throw new Error(message);
    }

    const data = await response.json();
    if (data.savedToHistory && historySaveBadge) {
      historySaveBadge.classList.remove("hidden");
    }

    renderStructuredResults(data.analysis || data);
  } catch (err) {
    console.error("[AI Resume Analyzer] Analysis failed:", err);
    showError(err.message || "Something went wrong during analysis. Please verify your API key and file format.");
  } finally {
    setLoading(false);
  }
});

// --- Live Market Skill Radar Action ---
scanMarketBtn?.addEventListener("click", async () => {
  showMarketError(null);
  marketResultsContainer?.classList.add("hidden");
  setMarketLoading(true);

  try {
    const formData = new FormData();
    formData.append("resume", state.file);
    formData.append("role", state.marketRole);

    const marketUrl = CONFIG.MARKET_TRENDS_URL || CONFIG.API_URL.replace("/analyze", "/market-trends");
    const response = await fetch(marketUrl, {
      method: "POST",
      body: formData,
    });

    if (!response.ok) {
      let message = `Request failed with status ${response.status}`;
      try {
        const errorBody = await response.json();
        message = errorBody.message || message;
      } catch (_) {}
      throw new Error(message);
    }

    const data = await response.json();
    renderMarketTrends(data);
  } catch (err) {
    console.error("[Live Market Radar] Scan failed:", err);
    showMarketError(err.message || "Failed to fetch live job market trends.");
  } finally {
    setMarketLoading(false);
  }
});

// --- Render Live Market Radar Results ---
function renderMarketTrends(data) {
  if (!data) return;

  if (marketRoleBadge) marketRoleBadge.textContent = data.targetRole || state.marketRole || "Target Role";
  if (totalPostingsBadge) totalPostingsBadge.textContent = data.totalPostingsAnalyzed || 0;

  // Render trending skills
  const skills = data.trendingSkills || [];
  if (trendingSkillsList) {
    if (skills.length > 0) {
      trendingSkillsList.innerHTML = skills.slice(0, 10).map(s => {
        const badge = s.presentInResume
          ? `<span class="text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-full">IN RESUME</span>`
          : `<span class="text-[10px] font-bold bg-rose-500/10 text-rose-400 border border-rose-500/30 px-2 py-0.5 rounded-full">MISSING</span>`;
        const barColor = s.presentInResume ? "bg-emerald-400" : "bg-rose-400";

        return `
          <div>
            <div class="flex justify-between items-center text-xs font-semibold text-slate-300 mb-1">
              <span class="flex items-center gap-2">${escapeHtml(s.skill)} ${badge}</span>
              <span class="font-mono text-slate-400">${s.count} postings (${s.percentage}%)</span>
            </div>
            <div class="w-full bg-slate-800 rounded-full h-2">
              <div class="${barColor} h-2 rounded-full transition-all duration-500" style="width: ${Math.min(100, s.percentage)}%"></div>
            </div>
          </div>
        `;
      }).join("");
    } else {
      trendingSkillsList.innerHTML = `<p class="text-xs text-slate-400">No skill trends found.</p>`;
    }
  }

  // Render missing skills
  const missing = data.topMissingHighDemandSkills || [];
  if (missingMarketSkillsBadges) {
    missingMarketSkillsBadges.innerHTML = missing.length > 0
      ? missing.map(m => `<span class="bg-rose-500/10 text-rose-300 border border-rose-500/30 px-3 py-1 rounded-full text-xs font-semibold">${escapeHtml(m)}</span>`).join("")
      : `<span class="text-xs text-slate-400">Your resume covers all high-demand market skills!</span>`;
  }

  // Render sample postings
  const postings = data.samplePostings || [];
  if (samplePostingsGrid) {
    samplePostingsGrid.innerHTML = postings.length > 0
      ? postings.map(p => `
          <a href="${escapeHtml(p.url)}" target="_blank" rel="noreferrer"
            class="block bg-white/5 border border-white/10 hover:border-rose-400/40 rounded-xl p-3 transition">
            <div class="flex justify-between items-start">
              <h5 class="text-xs font-bold text-white hover:text-rose-300 transition">${escapeHtml(p.title)}</h5>
              <span class="text-[10px] text-slate-500 font-mono">${escapeHtml(p.publicationDate)}</span>
            </div>
            <p class="text-[11px] text-slate-400 mt-1">🏢 ${escapeHtml(p.companyName)}</p>
          </a>
        `).join("")
      : `<p class="text-xs text-slate-400">No sample postings available.</p>`;
  }

  marketResultsContainer?.classList.remove("hidden");
  marketResultsContainer?.scrollIntoView({ behavior: "smooth", block: "start" });
}

// --- Render 5 Analysis Features ---
function renderStructuredResults(analysis) {
  if (!analysis) return;

  // 1. ATS Score Breakdown
  const ats = analysis.atsScore || {};
  const atsVal = ats.score || 0;
  if (atsScoreBadge) atsScoreBadge.textContent = `${atsVal}%`;
  
  const fmtVal = ats.breakdown?.formatting || atsVal;
  const kwVal = ats.breakdown?.keywordMatch || atsVal;
  const secVal = ats.breakdown?.sectionCompleteness || atsVal;

  if (scoreFormatVal) scoreFormatVal.textContent = `${fmtVal}%`;
  if (scoreFormatBar) scoreFormatBar.style.width = `${fmtVal}%`;

  if (scoreKeywordVal) scoreKeywordVal.textContent = `${kwVal}%`;
  if (scoreKeywordBar) scoreKeywordBar.style.width = `${kwVal}%`;

  if (scoreSectionVal) scoreSectionVal.textContent = `${secVal}%`;
  if (scoreSectionBar) scoreSectionBar.style.width = `${secVal}%`;

  if (atsSummary) atsSummary.textContent = ats.summary || "Evaluation completed for ATS parser standards.";

  // 4. Job Matching Fit
  const jobFit = analysis.jobMatching || {};
  const matchPct = jobFit.matchPercentage || atsVal;
  if (jobMatchBadge) jobMatchBadge.textContent = `${matchPct}%`;
  if (jobMatchReasoning) jobMatchReasoning.textContent = jobFit.reasoning || "Job match calculation finished.";

  if (keyStrengthsList) {
    keyStrengthsList.innerHTML = (jobFit.keyStrengths || ["Technical Skills Alignment"])
      .map(s => `<li>${escapeHtml(s)}</li>`).join("");
  }

  if (gapsList) {
    gapsList.innerHTML = (jobFit.gaps || ["Minor framework differences"])
      .map(g => `<li>${escapeHtml(g)}</li>`).join("");
  }

  // 2. Skill Gap Analysis
  const skillGap = analysis.skillGap || {};
  if (skillGapSummary) skillGapSummary.textContent = skillGap.summary || "Skill matrix evaluation against job requirement keywords.";

  if (missingSkillsBadges) {
    missingSkillsBadges.innerHTML = (skillGap.missingSkills || [])
      .map(s => `<span class="bg-rose-500/10 text-rose-300 border border-rose-500/30 px-3 py-1 rounded-full text-xs font-semibold">${escapeHtml(s)}</span>`)
      .join("") || `<span class="text-xs text-slate-400">No critical missing skills detected.</span>`;
  }

  if (matchingSkillsBadges) {
    matchingSkillsBadges.innerHTML = (skillGap.matchingSkills || [])
      .map(s => `<span class="bg-emerald-500/10 text-emerald-300 border border-emerald-500/30 px-3 py-1 rounded-full text-xs font-semibold">${escapeHtml(s)}</span>`)
      .join("") || `<span class="text-xs text-slate-400">No exact skill matches extracted.</span>`;
  }

  // 3. Resume Suggestions & Line Rewrites
  const suggestions = analysis.suggestions || {};
  const rewrites = suggestions.lineLevelRewrites || [];

  if (lineRewritesTable) {
    if (rewrites.length > 0) {
      lineRewritesTable.innerHTML = rewrites.map(rw => `
        <tr class="hover:bg-white/[0.02] transition">
          <td class="p-3 text-slate-400 font-mono text-[11px] leading-relaxed bg-rose-500/[0.03] border-r border-white/5">${escapeHtml(rw.original || "")}</td>
          <td class="p-3 text-emerald-300 font-semibold leading-relaxed bg-emerald-500/[0.03] border-r border-white/5">
            <span class="inline-block text-[10px] text-emerald-400 font-mono font-normal uppercase bg-emerald-500/10 px-1.5 py-0.5 rounded mr-1">Suggested</span>
            ${escapeHtml(rw.suggested || "")}
          </td>
          <td class="p-3 text-slate-300 leading-relaxed">${escapeHtml(rw.reason || "")}</td>
        </tr>
      `).join("");
    } else {
      lineRewritesTable.innerHTML = `<tr><td colspan="3" class="p-4 text-center text-slate-400">No specific line rewrites required.</td></tr>`;
    }
  }

  if (generalAdviceList) {
    generalAdviceList.innerHTML = (suggestions.generalAdvice || [
      "Use strong action verbs at the beginning of bullet points.",
      "Quantify your results with percentages and metric data."
    ]).map(adv => `<li>${escapeHtml(adv)}</li>`).join("");
  }

  // 5. Placement Interview Questions & Answers
  const questions = analysis.interviewQuestions || [];
  if (interviewQuestionsContainer) {
    if (questions.length > 0) {
      interviewQuestionsContainer.innerHTML = questions.map((q, idx) => `
        <div class="bg-white/5 border border-white/10 rounded-xl p-4 space-y-2 hover:border-indigo-400/40 transition">
          <div class="flex items-center justify-between gap-2">
            <span class="text-xs font-bold text-indigo-400 uppercase font-mono tracking-wider">
              Q${idx + 1} · ${escapeHtml(q.category || "Placement Question")}
            </span>
            <span class="text-[10px] bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 px-2 py-0.5 rounded-full font-medium">Interview Prep</span>
          </div>
          <p class="text-sm font-semibold text-white leading-relaxed">${escapeHtml(q.question || "")}</p>
          <div class="mt-2 pt-2 border-t border-white/5 bg-indigo-500/[0.04] p-3 rounded-lg border border-indigo-500/10">
            <span class="text-[11px] font-bold text-emerald-400 uppercase tracking-wide block mb-1">🎯 What a Strong Answer Should Cover:</span>
            <p class="text-xs text-slate-300 leading-relaxed">${escapeHtml(q.keyPointsToCover || "")}</p>
          </div>
        </div>
      `).join("");
    } else {
      interviewQuestionsContainer.innerHTML = `<p class="text-xs text-slate-400 text-center py-4">No specific interview questions generated.</p>`;
    }
  }

  resultsContainer?.classList.remove("hidden");
  resultsContainer?.scrollIntoView({ behavior: "smooth", block: "start" });
}

// --- Auth Modal & Supabase Actions ---
function showAuthModal() {
  authModal?.classList.remove("hidden");
  authError?.classList.add("hidden");
  authSuccess?.classList.add("hidden");
}

function hideAuthModal() {
  authModal?.classList.add("hidden");
}

openAuthModalBtn?.addEventListener("click", showAuthModal);
closeAuthModalBtn?.addEventListener("click", hideAuthModal);

tabSignin?.addEventListener("click", () => {
  state.activeAuthTab = "signin";
  if (tabSignin) tabSignin.className = "flex-1 pb-2 text-sm font-semibold text-emerald-400 border-b-2 border-emerald-400";
  if (tabSignup) tabSignup.className = "flex-1 pb-2 text-sm font-semibold text-slate-400 border-b-2 border-transparent";
  if (authSubmitBtn) authSubmitBtn.textContent = "Sign In";
  authError?.classList.add("hidden");
});

tabSignup?.addEventListener("click", () => {
  state.activeAuthTab = "signup";
  if (tabSignup) tabSignup.className = "flex-1 pb-2 text-sm font-semibold text-emerald-400 border-b-2 border-emerald-400";
  if (tabSignin) tabSignin.className = "flex-1 pb-2 text-sm font-semibold text-slate-400 border-b-2 border-transparent";
  if (authSubmitBtn) authSubmitBtn.textContent = "Sign Up";
  authError?.classList.add("hidden");
});

authForm?.addEventListener("submit", async (e) => {
  e.preventDefault();
  authError?.classList.add("hidden");
  authSuccess?.classList.add("hidden");

  const email = authEmail?.value.trim();
  const password = authPassword?.value.trim();

  if (!state.supabase) {
    if (authError) {
      authError.textContent = "Supabase client is not configured in config.js.";
      authError.classList.remove("hidden");
    }
    return;
  }

  if (authSubmitBtn) {
    authSubmitBtn.disabled = true;
    authSubmitBtn.textContent = "Processing...";
  }

  try {
    if (state.activeAuthTab === "signup") {
      const { data, error } = await state.supabase.auth.signUp({ email, password });
      if (error) throw error;
      if (authSuccess) {
        authSuccess.textContent = "Registration successful! Check your email or sign in.";
        authSuccess.classList.remove("hidden");
      }
    } else {
      const { data, error } = await state.supabase.auth.signInWithPassword({ email, password });
      if (error) throw error;
      state.currentUser = data.user;
      renderAuthNavState();
      hideAuthModal();
    }
  } catch (err) {
    if (authError) {
      authError.textContent = err.message || "Authentication error.";
      authError.classList.remove("hidden");
    }
  } finally {
    if (authSubmitBtn) {
      authSubmitBtn.disabled = false;
      authSubmitBtn.textContent = state.activeAuthTab === "signup" ? "Sign Up" : "Sign In";
    }
  }
});

async function handleSignOut() {
  if (state.supabase) {
    await state.supabase.auth.signOut();
  }
  state.currentUser = null;
  renderAuthNavState();
  resultsContainer?.classList.add("hidden");
}

// --- History Modal & Querying ---
historyNavBtn?.addEventListener("click", async () => {
  historyModal?.classList.remove("hidden");
  await fetchHistoryRecords();
});

closeHistoryModalBtn?.addEventListener("click", () => {
  historyModal?.classList.add("hidden");
});

async function fetchHistoryRecords() {
  if (!historyListContainer) return;
  if (!state.supabase || !state.currentUser) {
    historyListContainer.innerHTML = `<p class="text-xs text-rose-400 text-center py-6">You must be logged in to view history.</p>`;
    return;
  }

  try {
    historyListContainer.innerHTML = `<p class="text-xs text-slate-400 text-center py-6">Fetching history from Supabase...</p>`;
    const { data, error } = await state.supabase
      .from("analysis_history")
      .select("*")
      .order("created_at", { ascending: false });

    if (error) throw error;

    if (!data || data.length === 0) {
      historyListContainer.innerHTML = `<p class="text-xs text-slate-400 text-center py-8">No analysis history found yet. Perform a resume analysis to save history.</p>`;
      return;
    }

    historyListContainer.innerHTML = data.map(record => `
      <div class="bg-white/5 border border-white/10 hover:border-emerald-500/30 rounded-xl p-4 transition flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <div class="flex items-center gap-2">
            <span class="text-sm font-bold text-white">📄 ${escapeHtml(record.resume_filename)}</span>
            <span class="text-xs font-mono font-bold text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded-full">
              ATS ${record.ats_score}%
            </span>
          </div>
          <p class="text-xs text-slate-400 mt-1 line-clamp-1 max-w-md">${escapeHtml(record.job_description)}</p>
          <span class="text-[10px] text-slate-500 block mt-1 font-mono">${new Date(record.created_at).toLocaleString()}</span>
        </div>
        <button type="button" onclick="viewHistoryItem('${record.id}')"
          class="text-xs font-semibold text-emerald-300 border border-emerald-500/30 hover:bg-emerald-500/10 px-3 py-1.5 rounded-lg transition shrink-0">
          View Report
        </button>
      </div>
    `).join("");

    window.historyRecords = data;
  } catch (err) {
    console.error("[Supabase History] Fetch error:", err);
    historyListContainer.innerHTML = `<p class="text-xs text-rose-400 text-center py-6">Failed to load history: ${escapeHtml(err.message)}</p>`;
  }
}

window.viewHistoryItem = function(id) {
  const record = (window.historyRecords || []).find(r => r.id === id);
  if (record && record.analysis_json) {
    historyModal?.classList.add("hidden");
    renderStructuredResults(record.analysis_json);
  }
};

function escapeHtml(str) {
  if (typeof str !== "string") return "";
  return str.replace(/[&<>"']/g, (m) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[m]));
}

// --- Init ---
initSupabase();
refreshAnalyzeButton();
refreshMarketScanButton();
