// app.js — AI Resume Analyzer frontend logic (vanilla JS, no build step)

const API_URL = "http://localhost:8000/api/analyze";

const state = {
  apiKey: "",
  jobDescription: "",
  file: null,
  isLoading: false,
};

// --- Element refs ---
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
const resultsContainer = document.getElementById("resultsContainer");
const resultsContent = document.getElementById("resultsContent");

// --- Helpers ---
function refreshAnalyzeButton() {
  const ready = state.apiKey.trim().length > 0 && state.jobDescription.trim().length > 0 && !!state.file;
  analyzeBtn.disabled = !ready || state.isLoading;
}

function showError(message) {
  if (!message) {
    errorText.classList.add("hidden");
    errorText.textContent = "";
    return;
  }
  errorText.textContent = message;
  errorText.classList.remove("hidden");
}

function setLoading(isLoading) {
  state.isLoading = isLoading;
  analyzeBtn.classList.toggle("is-loading", isLoading);
  analyzeBtnLabel.innerHTML = isLoading
    ? '<span class="spinner"></span>Analyzing...'
    : "✨ Analyze Resume 🚀 →";
  refreshAnalyzeButton();
}

// --- API key field ---
apiKeyInput.addEventListener("input", (e) => {
  state.apiKey = e.target.value;
  refreshAnalyzeButton();
});

toggleVisibility.addEventListener("click", () => {
  apiKeyInput.type = apiKeyInput.type === "password" ? "text" : "password";
});

// --- Job description field + character counter ---
jdInput.addEventListener("input", (e) => {
  state.jobDescription = e.target.value;
  jdCharCount.textContent = `${e.target.value.length} chars`;
  refreshAnalyzeButton();
});

loadExampleBtn.addEventListener("click", () => {
  const example =
    "Senior Frontend Engineer — build and ship customer-facing features in React/TypeScript, " +
    "collaborate closely with design and backend teams, and own performance, accessibility, and " +
    "code quality across the product. 4+ years experience, strong CSS fundamentals, REST API integration.";
  jdInput.value = example;
  jdInput.dispatchEvent(new Event("input"));
});

// --- File handling ---
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
  state.file = file;
  dropzoneText.textContent = `${file.name} selected`;
  refreshAnalyzeButton();
}

browseBtn.addEventListener("click", (e) => {
  e.stopPropagation();
  fileInput.click();
});

dropzone.addEventListener("click", (e) => {
  if (e.target === browseBtn) return;
  fileInput.click();
});

fileInput.addEventListener("change", (e) => handleFile(e.target.files[0]));

// Smooth dragover / dragleave / drop handling
["dragenter", "dragover"].forEach((evt) => {
  dropzone.addEventListener(evt, (e) => {
    e.preventDefault();
    e.stopPropagation();
    dropzone.classList.add("dropzone--active");
  });
});

["dragleave", "dragend"].forEach((evt) => {
  dropzone.addEventListener(evt, (e) => {
    e.preventDefault();
    e.stopPropagation();
    dropzone.classList.remove("dropzone--active");
  });
});

dropzone.addEventListener("drop", (e) => {
  e.preventDefault();
  e.stopPropagation();
  dropzone.classList.remove("dropzone--active");
  const file = e.dataTransfer?.files?.[0];
  handleFile(file);
});

// Prevent the browser from navigating away if a file is dropped outside the zone
["dragover", "drop"].forEach((evt) => {
  window.addEventListener(evt, (e) => e.preventDefault());
});

// --- Analyze action ---
analyzeBtn.addEventListener("click", async () => {
  showError(null);
  resultsContainer.classList.add("hidden");
  setLoading(true);

  try {
    const formData = new FormData();
    formData.append("resume", state.file);
    formData.append("jobDescription", state.jobDescription);
    formData.append("apiKey", state.apiKey);

    const response = await fetch(API_URL, {
      method: "POST",
      body: formData,
    });

    if (!response.ok) {
      let message = `Request failed with status ${response.status}`;
      try {
        const errorBody = await response.json();
        message = errorBody.message || message;
      } catch (_) {
        /* body wasn't JSON — keep default message */
      }
      throw new Error(message);
    }

    const data = await response.json();
    renderResults(data);
  } catch (err) {
    console.error("[AI Resume Analyzer] Analysis failed:", err);
    showError(err.message || "Something went wrong. Please try again.");
  } finally {
    setLoading(false);
  }
});

// --- Render markdown output from the backend ---
function renderResults(data) {
  const markdown = data.analysisMarkdown || data.analysis || "_No analysis content was returned._";

  if (window.marked) {
    resultsContent.innerHTML = window.marked.parse(markdown);
  } else {
    // Fallback: render as plain text if marked.js failed to load
    const pre = document.createElement("pre");
    pre.className = "whitespace-pre-wrap text-sm text-slate-300";
    pre.textContent = markdown;
    resultsContent.innerHTML = "";
    resultsContent.appendChild(pre);
  }

  resultsContainer.classList.remove("hidden");
  resultsContainer.scrollIntoView({ behavior: "smooth", block: "start" });
}

// --- Init ---
refreshAnalyzeButton();
