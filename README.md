# 📄 AI Resume Analyzer (Enterprise DevSecOps Edition)

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Security](https://img.shields.io/badge/security-OWASP_Top_10-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Next.js](https://img.shields.io/badge/Next.js-14-black)
![License](https://img.shields.io/badge/license-MIT-green)

A highly secure, enterprise-grade SaaS platform that utilizes advanced Natural Language Processing (via Google Gemini 2.5 Flash) to evaluate resumes against job descriptions. 

This application is built with a **Security-First, Zero Trust Architecture**, featuring an ultra-premium Glassmorphic UI, robust rate-limiting, and transient memory handling to ensure absolute data privacy.

---

## 🛡️ Enterprise Security Posture

Security is the highest priority of this platform. It strictly adheres to **OWASP Top 10** recommendations:

* **Zero Trust Architecture:** Every API endpoint requires authentication.
* **Transient File Processing:** PDFs are processed entirely in-memory. Resumes are NEVER written to disk, preventing local file inclusion (LFI) or server exposure. Memory is aggressively garbage-collected post-analysis.
* **Secure Session Management:** Dual-token JWT system (short-lived Access + rotating Refresh). Tokens are delivered exclusively via `HttpOnly`, `Secure`, `SameSite=Strict` cookies to prevent XSS attacks.
* **Advanced Cryptography:** Passwords are mathematically hashed using Argon2/BCrypt.
* **Fortified Uploads:** Strict Multipart boundary validation, magic-byte MIME checking (blocking renamed `.exe` files), and a hard 5MB size limit.
* **Rate Limiting:** IP/User-based rate limiting via Bucket4j to prevent brute-force and DDoS attacks.
* **Data Privacy:** PII and Gemini API keys are never written to application logs.

---

## 🛠️ Technology Stack

### Frontend (Client Portal)
* **Framework:** Next.js 14 (App Router)
* **Styling:** Tailwind CSS (Premium Dark-Mode Glassmorphism)
* **Animations:** Framer Motion
* **Security:** Next.js Server Actions (Proxying requests to prevent client-side token leakage)

### Backend (Core API)
* **Framework:** Java 17 + Spring Boot 3
* **Security:** Spring Security 6 + JJWT
* **Parsing Engine:** Apache PDFBox (In-memory stream processing)
* **AI Integration:** Google Gemini REST API (via native `java.net.http.HttpClient`)

### Infrastructure
* **Database:** PostgreSQL (User data, audit logs, encrypted metadata)
* **Authentication:** Stateless JWT

---

## 📂 Repository Architecture

```text
ai-resume-analyzer/
├── frontend/                   # Next.js 14 Web Application
│   ├── app/                    # App router, layouts, and pages
│   ├── components/             # Reusable UI (Dropzones, Charts, Glass Cards)
│   ├── lib/                    # Server Actions & API Proxies
│   └── public/                 # Static assets
└── backend/                    # Spring Boot 3 Core API
    ├── src/main/java/com/app/
    │   ├── config/             # SecurityFilterChain, Bucket4j, CORS
    │   ├── controller/         # AuthController, AnalyzerController
    │   ├── filter/             # JwtAuthenticationFilter
    │   ├── service/            # TransientPDFService, GeminiClient
    │   └── util/               # FileSecurityValidator, JwtUtil
    └── src/main/resources/     # application.properties
