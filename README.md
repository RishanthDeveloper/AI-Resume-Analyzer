# 🛡️ AI Resume Analyzer — Secure Enterprise Edition

### A Zero Trust, OWASP-hardened resume intelligence platform — Next.js 14 (Server Actions) + Spring Boot 3 + Spring Security 6, powered by Gemini 2.5 Flash

<p align="left">
  <img src="https://img.shields.io/badge/status-active-brightgreen" alt="status" />
  <img src="https://img.shields.io/badge/security-Zero_Trust-critical" alt="Zero Trust" />
  <img src="https://img.shields.io/badge/Next.js-14-black?logo=next.js" alt="Next.js 14" />
  <img src="https://img.shields.io/badge/Java-17+-orange?logo=openjdk" alt="Java 17+" />
  <img src="https://img.shields.io/badge/Spring_Security-6-6DB33F?logo=spring" alt="Spring Security 6" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="license" />
</p>

Every endpoint requires authentication. Tokens never touch client-side JavaScript. Resume bytes never touch disk. Built to withstand scrutiny, not just demos.

---

## ✨ Features

- **🔒 Zero Trust Endpoints** — no unauthenticated route exists for analysis, upload, or user data; every request is verified regardless of origin.
- **🍪 HttpOnly Cookie Auth** — short-lived access tokens (15 min) and rotating refresh tokens (7 days), delivered as `HttpOnly`, `Secure`, `SameSite=Strict` cookies — never in JSON bodies, never in `localStorage`.
- **🔑 Argon2 Password Hashing** — memory-hard hashing for stored credentials, resistant to GPU/ASIC cracking.
- **🚦 Rate Limiting** — Bucket4j-backed per-IP/per-user throttling on auth and upload endpoints to blunt brute-force and DDoS attempts.
- **🧬 Transient File Processing** — resume bytes exist only in memory for the duration of parsing; nothing is written to disk or a database by default, and buffers are cleared after use.
- **🔍 Defense-in-Depth File Validation** — magic-byte verification (not just file extension), a hard 5MB limit, and filename sanitization before anything is parsed.
- **🧱 Secure Headers by Default** — HSTS, a restrictive Content-Security-Policy, and Spring Security's standard header hardening.
- **🙈 Privacy-Preserving Logging** — resume contents and LLM outputs are never written to application logs; only metadata (sizes, durations, status codes) is logged.
- **⚡ Next.js Server Actions** — the multipart upload is proxied server-side, so the Gemini key and auth cookies never reach the browser's JS runtime.

---

## 🛠️ Tech Stack

**Frontend**
- Next.js 14 (App Router, Server Actions)
- Tailwind CSS

**Backend**
- Java 17+, Spring Boot 3, Spring Security 6
- `jjwt` — JWT issuance/validation
- Apache PDFBox — in-memory PDF text extraction
- Bucket4j — rate limiting

**Data & AI**
- PostgreSQL — user accounts, audit logs, encrypted file metadata
- Google Gemini API (`gemini-2.5-flash`) via direct HTTPS calls

---

## 📂 Repository Architecture

```
ai-resume-analyzer-secure/
├── frontend/                                # Next.js 14 App Router project
│   ├── app/
│   │   ├── (auth)/login/page.tsx
│   │   ├── (auth)/register/page.tsx
│   │   ├── dashboard/page.tsx
│   │   └── actions.ts                       # Server Action: proxies multipart upload + cookies
│   └── tailwind.config.ts
│
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/airesumeanalyzer/backend/
│       ├── ResumeAnalyzerApplication.java
│       ├── security/
│       │   └── SecurityConfig.java          # Filter chain, CORS, CSRF, security headers
│       ├── filter/
│       │   └── JwtAuthenticationFilter.java # Extracts/validates JWT from HttpOnly cookie
│       ├── validator/
│       │   └── FileSecurityValidator.java   # Magic bytes, size limits, filename sanitization
│       ├── service/
│       │   └── SecureAnalysisService.java   # In-memory PDF parsing + Gemini integration
│       ├── controller/
│       │   ├── AuthController.java
│       │   └── ApiController.java
│       └── config/
│           └── RateLimitConfig.java         # Bucket4j configuration
│
├── .env.example
└── README.md
```

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/ai-resume-analyzer-secure.git
cd ai-resume-analyzer-secure
```

### 2. Backend setup

```bash
cd backend
```

Set required secrets as environment variables — **never hardcode these**:

```bash
export JWT_ACCESS_SECRET=$(openssl rand -base64 64)
export JWT_REFRESH_SECRET=$(openssl rand -base64 64)
export DB_URL=jdbc:postgresql://localhost:5432/resume_analyzer
export DB_USERNAME=postgres
export DB_PASSWORD=your_db_password
export GEMINI_API_KEY=your_google_gemini_api_key_here
```

Provision PostgreSQL locally (or via Docker):

```bash
docker run --name resume-analyzer-db -e POSTGRES_PASSWORD=your_db_password \
  -e POSTGRES_DB=resume_analyzer -p 5432:5432 -d postgres:16
```

Build and run:

```bash
mvn clean install
mvn spring-boot:run
```

API available at `http://localhost:8000`.

### 3. Frontend setup

```bash
cd frontend
npm install
```

Create `.env.local`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8000
```

Run the dev server:

```bash
npm run dev
```

Open `http://localhost:3000`.

---

## 🔐 Security Notes

- **Secrets**: all secrets (JWT signing keys, DB credentials, Gemini API key) are read via `System.getenv()` / Spring `@Value`. No secret is ever hardcoded or committed — `.env.example` documents required variables with empty placeholders only.
- **Token rotation**: refresh tokens are single-use and rotated on every refresh call; reuse of a stale refresh token invalidates the entire session family.
- **CSRF**: because auth relies on cookies rather than an `Authorization` header, state-changing requests require a matching CSRF token (double-submit cookie pattern) in addition to the session cookie.
- **File handling**: uploads are validated against PDF magic bytes before parsing, capped at 5MB, and never persisted to disk; byte buffers are dereferenced immediately after text extraction so they're eligible for garbage collection as early as possible.
- **Logging**: request/response logging interceptors are configured to log metadata only — resume text, job descriptions, and Gemini responses are explicitly excluded from all log statements.

This is foundational, production-oriented security architecture — not a substitute for a professional penetration test or security audit before handling real user data at scale.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push and open a Pull Request

## 📜 License

MIT License — see [LICENSE](./LICENSE).

---

<p align="center">
  Built with Zero Trust principles, Next.js, Spring Security, and Gemini 2.5 Flash
</p>
