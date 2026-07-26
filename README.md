# 📄 AI Resume Analyzer — Placement & ATS Intelligence Platform

> **Java 17 + Spring Boot 3 Backend | Vanilla HTML/CSS/JS Frontend | Gemini 2.5 Flash AI | Supabase Auth & History**

<p align="left">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?logo=springboot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17" />
  <img src="https://img.shields.io/badge/AI-Gemini_2.5_Flash-4285F4?logo=google" alt="Gemini 2.5 Flash" />
  <img src="https://img.shields.io/badge/Database-Supabase_PostgreSQL-3ECF8E?logo=supabase" alt="Supabase" />
  <img src="https://img.shields.io/badge/Frontend-Vanilla_JS-F7DF1E?logo=javascript" alt="Vanilla JS" />
  <img src="https://img.shields.io/badge/PDF_Parser-Apache_PDFBox_3-blue" alt="PDFBox" />
</p>

An end-to-end AI-powered resume analysis and placement interview preparation application. It parses uploaded PDF resumes in-memory using **Apache PDFBox 3.x**, evaluates them against target job descriptions using **Google Gemini 2.5 Flash**, and provides structured, actionable insights tailored for competitive placement interviews. Authentication and historical report persistence are powered by **Supabase (PostgreSQL + RLS)**.

---

## ⚡ Key Features

- **📊 ATS Compatibility Score (0-100)**: Evaluates formatting, keyword density, and section completeness.
- **🔍 Skill Gap Analysis**: Identifies exact technical and domain skills present vs. missing relative to the job posting.
- **✍️ Line-Level Resume Suggestions**: Gives concrete line-by-line rewrite suggestions with explanations to improve resume impact.
- **🎯 Job Fit & Match Score**: Highlights candidate strengths and gap areas against the job requirements.
- **🎓 Placement Interview Questions**: Generates 5–8 targeted interview questions based on the candidate's specific background and the target role, complete with "What a strong answer should cover" guidance.
- **🔐 Supabase Auth & RLS History**: Enables user login/signup via email & password and stores past resume evaluations securely in Supabase PostgreSQL protected by Row Level Security (RLS).
- **🛡️ Zero-Trust Security**: Per-request Gemini API key passed directly from the browser; resume bytes exist strictly in-memory during extraction and are never saved on application disk.

---

## 🏗️ Architecture & Technology Stack

```
resume-analyzer-zero-trust/
├── backend/                                  # Spring Boot 3.2.5 Java 17 service
│   ├── Dockerfile                           # Multi-stage Docker build for Render/container deployment
│   ├── pom.xml                              # Maven dependencies (Spring Web, PDFBox 3.0.2, Jackson)
│   └── src/main/
│       ├── java/com/airesumeanalyzer/backend/
│       │   ├── ResumeAnalyzerApplication.java
│       │   ├── controller/ApiController.java # REST API endpoints (/api/analyze, /api/health) & CORS
│       │   └── service/
│       │       ├── AnalysisService.java     # PDFBox parsing & direct REST calls to Gemini 2.5 Flash
│       │       └── SupabaseService.java     # Backend history persistence via Supabase REST API (service_role)
│       └── resources/
│           └── application.properties       # Spring Boot config ($PORT, Gemini model, upload limits)
│
├── frontend/                                 # Lightweight Vanilla Web Application
│   ├── config.js                            # Easy configuration point for API_URL & Supabase keys
│   ├── index.html                           # Modern responsive UI with Tailwind & modals
│   ├── app.js                               # Interactive dashboard & Supabase auth/history handlers
│   └── styles.css                           # Glassmorphic dark design system & animations
│
├── .env.example                              # Environment variable documentation
├── render.yaml                               # Render Blueprint for backend deployment
├── supabase_schema.sql                       # Database migration & RLS policy setup script
└── README.md
```

---

## 🚀 Quick Start — Local Development

### 1. Prerequisites
- **Java 17 JDK** or higher installed
- **Apache Maven 3.8+**
- **Google Gemini API Key** ([Get free key from Google AI Studio](https://aistudio.google.com/app/apikey))
- **Supabase Account** ([Create free project at Supabase.com](https://supabase.com))

---

### 2. Set Up Supabase Database & Auth

1. Go to your Supabase Project Dashboard -> **SQL Editor**.
2. Run the SQL script from [`supabase_schema.sql`](./supabase_schema.sql) to create the `analysis_history` table and apply Row Level Security (RLS) policies.
3. Obtain your credentials from **Project Settings -> API**:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`
   - `SUPABASE_SERVICE_KEY`

---

### 3. Backend Setup (Spring Boot)

Navigate to the `backend` directory:

```bash
cd backend
```

Set optional environment variables or copy values from `.env.example`:

```bash
# Environment variables (Linux/macOS)
export SUPABASE_URL="https://your-project.supabase.co"
export SUPABASE_SERVICE_KEY="your-supabase-service-role-key"
export CORS_ALLOWED_ORIGINS="http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000"

# PowerShell (Windows)
$env:SUPABASE_URL="https://your-project.supabase.co"
$env:SUPABASE_SERVICE_KEY="your-supabase-service-role-key"
```

Build and run the application:

```bash
mvn clean package
mvn spring-boot:run
```

The Spring Boot backend will start on **`http://localhost:8000`**. You can verify health at `http://localhost:8000/api/health`.

---

### 4. Frontend Setup

1. Open [`frontend/config.js`](./frontend/config.js) and fill in your Supabase credentials:

```javascript
window.APP_CONFIG = {
  API_URL: "http://localhost:8000/api/analyze",
  SUPABASE_URL: "https://your-project.supabase.co",
  SUPABASE_ANON_KEY: "your-supabase-anon-key"
};
```

2. Serve the `frontend` folder using any static server (e.g. VS Code Live Server, `npx serve frontend`, or Python HTTP server):

```bash
# Using npx serve
npx serve frontend -p 5500

# Or using Python
cd frontend
python -m http.server 5500
```

3. Open `http://localhost:5500` in your browser.

---

## 🌐 Production Deployment Guide

### A. Deploy Backend to Render (Docker)

1. Push your repository to GitHub.
2. Log into [Render.com](https://render.com) and click **New -> Blueprint**.
3. Connect your GitHub repository `resume-analyzer-zero-trust`. Render will detect [`render.yaml`](./render.yaml).
4. Configure the environment variables in the Render dashboard:
   - `SUPABASE_URL`: Your Supabase Project URL
   - `SUPABASE_SERVICE_KEY`: Your Supabase `service_role` key
   - `CORS_ALLOWED_ORIGINS`: `https://your-frontend.vercel.app`
5. Click **Apply**. Render will build the Docker container and expose your API (e.g. `https://resume-analyzer-backend.onrender.com`).

---

### B. Deploy Frontend to Vercel

1. Log into [Vercel](https://vercel.com) and click **Add New -> Project**.
2. Import your GitHub repository `resume-analyzer-zero-trust`.
3. Set **Root Directory** to `frontend`.
4. Leave framework predefined as **Other** (Static Site).
5. In `frontend/config.js`, update `API_URL` to point to your deployed Render URL:
   ```javascript
   API_URL: "https://resume-analyzer-backend.onrender.com/api/analyze"
   ```
6. Click **Deploy**.

---

### C. Update CORS Configuration

After deploying your frontend to Vercel, update the `CORS_ALLOWED_ORIGINS` env variable on Render to include your Vercel URL (e.g. `https://resume-analyzer-zero-trust.vercel.app`), or add it to the `@CrossOrigin` origins array in [`ApiController.java`](./backend/src/main/java/com/airesumeanalyzer/backend/controller/ApiController.java).

---

## 📝 API Endpoints

### `POST /api/analyze`
Consumes `multipart/form-data`:
- `resume` (PDF file, max 10MB)
- `jobDescription` (String, required)
- `apiKey` (Gemini API Key, required)
- `userId` (Supabase User UUID, optional for history saving)

**Sample JSON Response Structure**:
```json
{
  "analysis": {
    "atsScore": {
      "score": 85,
      "breakdown": {
        "formatting": 90,
        "keywordMatch": 80,
        "sectionCompleteness": 85
      },
      "summary": "Strong overall formatting and structure."
    },
    "skillGap": {
      "missingSkills": ["Docker", "Kubernetes"],
      "matchingSkills": ["Java 17", "Spring Boot"],
      "summary": "Missing containerization skills mentioned in job description."
    },
    "suggestions": {
      "lineLevelRewrites": [
        {
          "original": "Worked on backend service with Spring",
          "suggested": "Architected microservices using Spring Boot 3.2, reducing API latency by 35%",
          "reason": "Quantifies results and includes concrete framework details."
        }
      ],
      "generalAdvice": ["Include metric-driven bullet points."]
    },
    "jobMatching": {
      "matchPercentage": 82,
      "reasoning": "High overlap with Java development requirements.",
      "keyStrengths": ["Core Java", "REST APIs"],
      "gaps": ["Cloud Deployment"]
    },
    "interviewQuestions": [
      {
        "question": "How did you structure your REST APIs in Spring Boot for scalability?",
        "category": "Technical Architecture",
        "keyPointsToCover": "Stateless design, exception handling, DTO layer."
      }
    ]
  },
  "savedToHistory": true,
  "timestamp": "2026-07-25T16:30:00Z"
}
```

### `GET /api/health`
Returns `{"status": "OK"}` to verify backend readiness.

---

## 📄 License
Licensed under the [MIT License](./LICENSE).
