# 📄 AI Resume Analyzer — Placement & ATS Intelligence Platform

> **Java 17 + Spring Boot 3 Backend | Vanilla HTML/CSS/JS Frontend | Gemini 2.5 Flash AI | Supabase Auth & History**

<p align="left">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?logo=springboot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17" />
  <img src="https://img.shields.io/badge/AI-Gemini_2.5_Flash-4285F4?logo=google" alt="Gemini 2.5 Flash" />
  <img src="https://img.shields.io/badge/Database-Supabase_PostgreSQL-3ECF8E?logo=supabase" alt="Supabase" />
  <img src="https://img.shields.io/badge/Security-Spring_Security_6-red" alt="Spring Security" />
  <img src="https://img.shields.io/badge/Rate_Limiter-Bucket4j-blue" alt="Bucket4j" />
  <img src="https://img.shields.io/badge/License-MIT-green" alt="MIT License" />
</p>

An end-to-end AI-powered resume analysis and placement interview preparation application. It parses uploaded PDF resumes in-memory using **Apache PDFBox 3.x**, evaluates them against target job descriptions using **Google Gemini 2.5 Flash**, and provides structured, actionable insights tailored for competitive placement interviews. Authentication and historical report persistence are powered by **Supabase (PostgreSQL + RLS)**.

---

## 🏗️ Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                                 USER BROWSER                                      |
|  - Vanilla HTML/CSS/JS (Tailwind, Supabase JS v2)                                 |
|  - Authentication via Supabase Auth                                               |
|  - Per-Request Gemini API Key + Supabase Bearer JWT Token                         |
+----------------------------------------+------------------------------------------+
                                         |
                                         | HTTPS / REST API
                                         v
+----------------------------------------+------------------------------------------+
|                            SPRING BOOT 3 BACKEND                                  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | Security & Filtering Layer                                                  |  |
|  | - CorsConfig (Allowed Origins)                                             |  |
|  | - SecurityConfig (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)        |  |
|  | - RateLimiterService (Bucket4j 10 req/min per IP & User)                    |  |
|  | - SupabaseAuthService (Server-Side Bearer Token Verification)               |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        |                                          |
|                                        v                                          |
|  +-------------------------------------+---------------------------------------+  |
|  | ApiController (/api/analyze, /api/health)                                   |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        |                                          |
|  +-------------------------------------+---------------------------------------+  |
|  | Modular Service Layer                                                       |  |
|  | - PdfTextExtractor (5s Timeout, PDFBox 3.x)                                 |  |
|  | - GeminiPromptBuilder (XML Boundary Defenses)                                 |  |
|  | - GeminiClient (REST Client to Google Gemini 2.5 Flash)                      |  |
|  | - AnalysisResponseValidator (DTO Schema Enforcement)                         |  |
|  | - SupabaseService (HistoryRepository Implementation via service_role key)    |  |
|  +-------------------------------------+---------------------------------------+  |
+----------------------------------------+------------------------------------------+
                                         |
                       +-----------------+-----------------+
                       |                                   |
                       v                                   v
+----------------------+------------------+   +------------+------------------------+
|       GOOGLE GEMINI 2.5 FLASH API       |   |       SUPABASE POSTGRESQL DB       |
| - generateContent REST Endpoint         |   | - analysis_history Table (RLS)          |
+-----------------------------------------+   +-------------------------------------+
```

---

## ⚡ Key Features

- **📊 ATS Compatibility Score (0-100)**: Evaluates formatting, keyword density, and section completeness.
- **🔍 Skill Gap Analysis**: Identifies exact technical and domain skills present vs. missing relative to the job posting.
- **✍️ Line-Level Resume Suggestions**: Gives concrete line-by-line rewrite suggestions with explanations to improve resume impact.
- **🎯 Job Fit & Match Score**: Highlights candidate strengths and gap areas against the job requirements.
- **🎓 Placement Interview Questions**: Generates 5–8 targeted interview questions based on the candidate's specific background and the target role, complete with "What a strong answer should cover" guidance.
- **🔐 Supabase Auth & Verified RLS History**: Enables user login/signup via email & password and stores past resume evaluations securely in Supabase PostgreSQL protected by verified server-side JWT authentication.
- **🛡️ Zero-Trust Security**: Server-side token verification, Bucket4j rate-limiting, CSP, HSTS, security headers, and non-root Docker execution.

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

Set environment variables or copy values from `.env.example`:

```bash
# Environment variables (Linux/macOS)
export SUPABASE_URL="https://your-project.supabase.co"
export SUPABASE_ANON_KEY="your-supabase-anon-key"
export SUPABASE_SERVICE_KEY="your-supabase-service-role-key"
export CORS_ALLOWED_ORIGINS="http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000"

# PowerShell (Windows)
$env:SUPABASE_URL="https://your-project.supabase.co"
$env:SUPABASE_ANON_KEY="your-supabase-anon-key"
$env:SUPABASE_SERVICE_KEY="your-supabase-service-role-key"
```

Build and test the application:

```bash
mvn clean test
mvn spring-boot:run
```

The Spring Boot backend will start on **`http://localhost:8000`**. You can verify health and dependency reachability at `http://localhost:8000/api/health`.

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
```

3. Open `http://localhost:5500` in your browser.

---

## 🌐 Production Deployment Guide

### A. Deploy Backend to Render (Docker)

1. Push your repository to GitHub.
2. Log into [Render.com](https://render.com) and click **New -> Blueprint**.
3. Connect your GitHub repository `resume-analyzer-zero-trust`. Render will detect [`render.yaml`](./render.yaml).
4. Configure environment variables in Render Dashboard (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_KEY`, `CORS_ALLOWED_ORIGINS`).
5. Render will build the Docker container using `USER appuser` and execute health check probes.

---

### B. Deploy Frontend to Vercel

1. Import your GitHub repository `resume-analyzer-zero-trust` to Vercel.
2. Set **Root Directory** to `frontend`.
3. Vercel will automatically route requests using [`vercel.json`](./vercel.json).

---

## 📝 API Documentation

### `POST /api/analyze`
Consumes `multipart/form-data`:
- `resume` (PDF file, max 10MB)
- `jobDescription` (String, required, max 5000 chars)
- `apiKey` (Gemini API Key, required)
- `Authorization` header (`Bearer <token>`, optional for authenticated history saving)

**Response Structure (`200 OK`)**:
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
  "timestamp": "2026-07-29T15:00:00Z"
}
```

### `GET /api/health`
Returns system and downstream dependency reachability status:
```json
{
  "status": "UP",
  "service": "AI Resume Analyzer Backend",
  "timestamp": "2026-07-29T15:00:00Z",
  "dependencies": {
    "supabase": "UP",
    "gemini": "UP"
  }
}
```

---

## 📄 License
Licensed under the [MIT License](./LICENSE).
