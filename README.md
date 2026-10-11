# Safety Desk (Android)

An Android AI assistant for the safety manager of a high-rise construction site in Maharashtra.
Its behaviour comes from the master instructions in `app/src/main/assets/agent_instructions.md`;
Claude (Anthropic API) does the analysis and writing.

## Install on your phone

1. Open the **Releases** page of this repository on your phone and download the latest `SafetyDesk-…apk`.
2. Open the file. If Android asks, allow installing apps from this source.
3. Open Safety Desk and paste your Anthropic API key (create one at console.anthropic.com → API keys).

Each push to `main` builds a new APK automatically (GitHub Actions → Build APK) and publishes it as a release.

## What it does

- **Ask the agent** – chat with optional web search for current laws and official sources, and photo attachments.
- **Photo check** – take or choose a site photo; get a Section 8 inspection report and add the findings to the register.
- **WhatsApp import** – in WhatsApp use *Export chat → Without media → Safety Desk*; the app summarises the observations and turns them into findings.
- **Findings register** – every field from Section 4 (ID, tower/floor/location, consequence, 5×5 risk, immediate control, CAPA, responsible, target date, closure evidence, verification). A finding can only be closed with evidence and the verifier's name. CSV export.
- **Prepare a document** – 34 ready-to-use documents: inspection checklists, permit and risk-assessment review, HIRA, JSA, SOP, toolbox talks (English/Hindi/Marathi), training plans, EHS reports and KPI summaries from the register, agendas, MoM, presentation outlines, emergency plans, drill evaluations, incident investigations, contractor notices, warning letters, CARs, scorecards, penalty and stop-work recommendations, escalation emails, safety alerts, purchase requests, and researched legal/compliance answers.
- **Escalation** – lists critical, overdue and long-open items and serious incidents, drafts the email and opens it in your email app for you to review and send.
- Every output can be copied, shared (WhatsApp, Gmail…), saved to Downloads, printed or saved as PDF, or kept in the app.

Data stays on the phone (Settings → Save backup). Requires Android 10 or newer.

## Build locally

`./gradlew assembleRelease` with JDK 17 and the Android SDK (platform 35).

## Trackers (v1.0.6)
Fall protection, plant & equipment, TPI inspection, environment, air pollution & dust control, safety committee, labour camp and BOCW compliance trackers. Each watches its due dates, exports to CSV and can prepare a report with AI.

## ISO 45001 & BOCW documents
Management review (9.3), internal audit (9.2), evaluation of compliance (9.1.2), objectives & performance (6.2, 9.1), nonconformity & corrective action (10.2), BOCW compliance status, BOCW registers & returns checklist, safety committee formation, labour camp checklist and labour camp documents.
