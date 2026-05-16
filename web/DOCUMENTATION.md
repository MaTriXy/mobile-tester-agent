## Overview

`mobile-tester-agent-frontend` is the React-based dashboard for the [Mobile Tester Agent](https://github.com/maikotrindade/mobile-tester-agent) system — an AI-powered platform that automates Android UI testing. The frontend lets users author test scenarios (a goal + ordered steps), persist them in Firestore, and dispatch them to the backend agent for execution against a real or emulated Android device.

This document covers the project's architecture, structure, configuration, runtime behavior, and development workflow.

---

## Architecture

```
┌──────────────────┐      HTTP / JSON       ┌─────────────────────┐
│   Frontend (this │ ─────────────────────▶ │  Backend Agent      │
│   repo) — React  │                        │  (Koog + Ktor)      │
│   + Vite         │ ◀───── reports ─────── │                     │
└────────┬─────────┘                        └──────────┬──────────┘
         │                                             │
         │ Firestore SDK                               │ ADB
         ▼                                             ▼
┌──────────────────┐                        ┌─────────────────────┐
│  Firebase /      │                        │  Android Device     │
│  Firestore       │                        │  / Emulator         │
│  (testScenarios) │                        └─────────────────────┘
└──────────────────┘
```

- **Frontend (this repo):** scenario CRUD UI, configuration UI, dispatches test runs.
- **Backend:** receives `POST /run-test`, drives the agent loop using a configured LLM, executes UI actions via ADB.
- **Firestore:** stores reusable test scenarios (real-time sync across clients).
- **Vite dev proxy:** any `/api/*` request is forwarded to `http://localhost:8080` (the backend) — see [vite.config.ts](vite.config.ts).

---

## Tech Stack

| Layer        | Library / Tool                  | Notes                                      |
| ------------ | ------------------------------- | ------------------------------------------ |
| UI framework | React 19                        | Function components + hooks                |
| Language     | TypeScript ~5.8                 | Strict-ish setup via tsconfig              |
| Build / dev  | Vite 7                          | Dev proxy for `/api` to backend on `:8080` |
| Routing      | react-router-dom v7             | `BrowserRouter` in [App.tsx](src/App.tsx)  |
| HTTP client  | axios                           | Used for `run-test` dispatch               |
| Persistence  | Firebase / Firestore            | Real-time scenario sync                    |
| Diagrams     | mermaid                         | Renders architecture in About page         |
| Styling      | CSS Modules (`*.module.css`)    | Co-located per page/component              |
| Lint         | ESLint 9 + typescript-eslint    | Flat config in [eslint.config.js](eslint.config.js) |

---

## Project Structure

```
mobile-tester-agent-frontend/
├── index.html                  # Vite entry document
├── vite.config.ts              # Dev server + /api proxy
├── eslint.config.js
├── tsconfig*.json
├── package.json
├── public/
│   └── robot.png
└── src/
    ├── main.tsx                # React root mount
    ├── App.tsx                 # Router + layout (TopNav / Footer)
    ├── App.css
    ├── index.css               # Global styles + theme variables
    ├── firebase.ts             # Firebase init, exports `db`
    ├── useTheme.ts             # Light/dark theme hook
    ├── TopNav.tsx              # Top navigation bar
    ├── TopNav.module.css
    ├── Footer.tsx
    ├── Footer.module.css
    ├── assets/
    └── pages/
        ├── home/               # Scenario authoring + test run
        │   ├── Home.tsx
        │   └── Home.module.css
        ├── settings/           # AI + agent configuration
        │   ├── Settings.tsx
        │   └── Settings.module.css
        └── about/              # System overview + Mermaid diagram
            ├── About.tsx
            └── About.module.css
```

---

## Routes

Defined in [App.tsx](src/App.tsx):

| Path        | Component                                  | Purpose                                       |
| ----------- | ------------------------------------------ | --------------------------------------------- |
| `/`         | [Home](src/pages/home/Home.tsx)            | Author, persist, and run test scenarios       |
| `/settings` | [Settings](src/pages/settings/Settings.tsx) | Configure AI model + agent runtime settings  |
| `/about`    | [About](src/pages/about/About.tsx)         | High-level architecture & links to repos      |

---

## Pages in Detail

### Home — Scenario Authoring (`/`)
Source: [src/pages/home/Home.tsx](src/pages/home/Home.tsx)

- **Scenario model**
  ```ts
  interface TestScenario {
    id: string;
    name: string;        // mirrors `goal` for now
    goal: string;
    steps: { id: number; description: string }[];
    createdAt: Date;
    updatedAt: Date;
  }
  ```
- **Two-panel layout**
  - Left: list of saved scenarios (real-time `onSnapshot` from Firestore `testScenarios` collection).
  - Right: editor for goal + ordered steps (add / edit inline / delete / reorder by id).
- **Auto-save:** any change to `testGoal` or `steps` triggers an upsert into Firestore (`addDoc` for new, `updateDoc` for existing).
- **Run test:** `POST /api/run-test` with payload:
  ```json
  {
    "goal": "<test goal>",
    "packageName": "<from localStorage or default>",
    "steps": ["step 1 description", "step 2 description", "..."]
  }
  ```
  Timeout is 3 minutes; rich error mapping for `ECONNABORTED`, `ERR_NETWORK`, HTTP error statuses.

### Settings — Agent Configuration (`/settings`)
Source: [src/pages/settings/Settings.tsx](src/pages/settings/Settings.tsx)

All settings persist to `localStorage` and POST to `/api/config`:

| Setting               | Key (localStorage)      | Default                                       |
| --------------------- | ----------------------- | --------------------------------------------- |
| Default AI model      | `executorInfoId`        | _(unset — required to enable Save)_           |
| LLM temperature       | `llmTemperature`        | `0.2` (range slider 0–10, step 0.2)           |
| Max agent iterations  | `maxAgentIterations`    | `50` (1–200)                                  |
| Log tokens consumption| `logTokensConsumption`  | `true`                                        |
| API base URL          | `apiBaseUrl`            | `http://localhost:8080`                       |
| Android package name  | `packageName`           | `io.githib.maikotrindade.appfortesting`       |

Supported model IDs (`executorInfoId`): `open_router`, `ollama_gwen`, `gemini`, `ollama_llama`.

### About — System Overview (`/about`)
Source: [src/pages/about/About.tsx](src/pages/about/About.tsx)

Static page with a card describing the backend and a Mermaid flow diagram. Mermaid is re-initialized when the active theme changes (via [useTheme](src/useTheme.ts)) so the diagram restyles for light/dark.

---

## Firebase / Firestore

Initialization: [src/firebase.ts](src/firebase.ts). All config values come from Vite env vars (`VITE_FIREBASE_*`) — see [Environment Variables](#environment-variables) below.

- **Collection:** `testScenarios`
- **Access pattern:** `onSnapshot` for live updates; `addDoc` / `updateDoc` / `deleteDoc` for mutations.
- **Document fields:** `name`, `goal`, `steps[]`, `createdAt` (Firestore Timestamp), `updatedAt` (Firestore Timestamp).

No auth is wired up — the client uses Firestore directly. Lock down `testScenarios` with Security Rules before exposing the app publicly.

---

## Backend API Contract

The frontend talks to the backend through the Vite proxy (`/api/*` → `http://localhost:8080/*`). Two endpoints are used today:

### `POST /api/run-test`
Triggers a test run. Request:
```json
{
  "goal": "string",
  "packageName": "string",
  "steps": ["string", "..."]
}
```
Timeout: 180 s. The response payload is logged but not surfaced beyond a success toast.

### `POST /api/config`
Persists agent settings server-side. Request:
```json
{
  "executorInfoId": "open_router | ollama_gwen | gemini | ollama_llama",
  "llmTemperature": 0.2,
  "maxAgentIterations": 50,
  "logTokensConsumption": true,
  "apiBaseUrl": "http://localhost:8080",
  "packageName": "io.githib.maikotrindade.appfortesting"
}
```

---

## Environment Variables

Vite exposes any variable prefixed with `VITE_` to the client at build time. Create a local `.env` at the repo root:

```bash
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=...
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
```

> ⚠️ All `VITE_*` values ship in the client bundle. Do not put secrets here — only public Firebase config.

---

## Getting Started

```bash
git clone https://github.com/maikotrindade/mobile-tester-agent-frontend.git
cd mobile-tester-agent-frontend
npm install
npm run dev
```

- Dev server: defaults to Vite's port (typically `http://localhost:5173`).
- Backend must be reachable at `http://localhost:8080` (or update the proxy target in [vite.config.ts](vite.config.ts)).

### Scripts
| Script           | What it does                                  |
| ---------------- | --------------------------------------------- |
| `npm run dev`    | Start Vite dev server with HMR + `/api` proxy |
| `npm run build`  | `tsc -b` then Vite production build into `dist/` |
| `npm run preview`| Serve the built `dist/` for smoke-testing     |
| `npm run lint`   | ESLint over the repo                          |

---

## Development Notes

- **Styling:** CSS Modules — import `styles from './X.module.css'` and reference `styles.className`. Global tokens (colors, theme vars) live in [index.css](src/index.css).
- **Cross-page styles:** `Settings.tsx` and `About.tsx` intentionally import `Home.module.css` (`homeStyles`) to reuse the two-panel layout primitives (`mainLayout`, `leftPanel`, `rightPanel`, `panelTitle`).
- **Theme:** [useTheme.ts](src/useTheme.ts) drives light/dark; the About page reruns `mermaid.initialize` whenever theme changes.
- **Adding a route:** create `src/pages/<feature>/<Component>.tsx`, register it in [App.tsx](src/App.tsx), and add a nav entry in [TopNav.tsx](src/TopNav.tsx).
- **Backend on a non-default port/host:** change `server.proxy['/api'].target` in [vite.config.ts](vite.config.ts), or run the frontend against a different `apiBaseUrl` (note: today the run-test request uses the relative `/api/run-test` path, so the proxy is the canonical lever).

---

## Known Limitations / TODOs

- Scenario `name` is currently always set to the `goal` value — no separate naming UI.
- No Firestore security rules in this repo; assume open dev rules.
- `executorInfoId` is required to save Settings, but other fields can be saved with an empty selection only via direct localStorage manipulation.
- The `apiBaseUrl` setting is stored but not yet used to override the proxy/relative paths in `run-test`.
- Test run responses are logged to the console only; there is no report viewer yet (reports live on the backend).

---

## Related Repositories

- [Mobile Tester Agent (Backend)](https://github.com/maikotrindade/mobile-tester-agent) — Koog + Ktor agent.
- [Sample Android App](https://github.com/maikotrindade/mobile-tester-agent-sample-app) — default target for the agent.
- [Koog Documentation](https://docs.koog.ai) — agent orchestration framework.
