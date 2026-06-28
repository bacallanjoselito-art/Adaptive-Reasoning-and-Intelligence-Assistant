  (70%)

  By domain:
    technology  ████████░░  80% (4/5)
    narrative   ██████░░░░  60% (3/5)

  Target accuracy: 75%+

This is how the "measurable 75%+ accuracy" goal becomes real — ORACLE now has a number, not just a claim.

#### Panel tabs
- Open: all unresolved predictions with outcome buttons
- Resolved: all closed predictions with colored badges (green / amber / red)
- Accuracy Stats: overall + per-domain accuracy charts
- Episodic Memory: view and delete memory entries for the current world context

---

### 4. WORLD-SCOPED EPISODIC MEMORY

ARIA now maintains persistent narrative memory per world context — a log of what was discussed, what was predicted, what happened — scoped to each world and injected into every session automatically.

#### How it fills
- Track a prediction → [PREDICTION] entry logged automatically
- Mark an outcome → [OUTCOME] entry logged with what happened
- Manual entries can be added from the Episodic Memory tab in the Prediction Log panel

#### How it feeds back in
Every time a system prompt is built, ARIA appends the last 10 memory entries for the active world:

```
EPISODIC MEMORY — what ARIA remembers about this world across sessions:
  [2026-06-14] [PREDICTION] Asked about rune balance in fire-water meta...
  [2026-06-21] [OUTCOME] ORACLE prediction was correct — conflict did break the meta
  [2026-06-21] [PREDICTION] Asked about the next balance issue...
```

ARIA reads this before every response. She does not just know the current world — she knows the history of what you have worked through in it across sessions.

Data stored in data/episodic_memory.json. Up to 60 entries per world context (oldest trimmed automatically). Scoped by world name — each world has its own memory.

---

### 5. WORLD CONTEXT SYSTEM

ARIA supports multiple named world context profiles — alternative realities, settings, or roles she can inhabit. One can be active at a time.

Each profile stores:
- world_name        → short card title (2-4 words)
- world_description → full setting, lore, atmosphere
- aria_role         → who ARIA is in this world
- knowledge_bounds  → what she knows
- knowledge_limits  → what she doesn't know / cannot do

How to create — two methods:

Method A — Describe It:
Type a sentence like:
  "We're in a dystopian city where ARIA is a black-market data broker. She knows every criminal network but has no access to government systems."
Hit Generate Fields — ARIA calls your configured LLM and populates all five fields automatically. Review and edit before saving.

Method B — Manual:
Fill in each field directly.

Card management:
- Activate / Deactivate (one active at a time)
- Edit any field
- Delete (with confirmation)
- Deactivate All (return to real world)

Active context name displays in the top bar. Profiles persist in config/world_contexts.json.

---

### 6. SETTINGS PANEL

Full configuration inside the app — no manual .env editing required:

| Setting        | Options                                                                 |
|----------------|-------------------------------------------------------------------------|
| Groq API Key   | Any gsk_... key (free at console.groq.com)                              |
| Anthropic Key  | Any sk-ant-... key                                                      |
| OpenAI Key     | Any sk-... key                                                          |
| Provider       | Groq / Claude / OpenAI (radio select)                                   |
| Claude Model   | haiku-4-5, sonnet-4-5, claude-3-haiku, claude-3-sonnet, claude-3.5-sonnet |
| OpenAI Model   | gpt-4o-mini, gpt-4o, gpt-4-turbo, gpt-3.5-turbo                        |
| Groq Model     | llama-3.3-70b, llama-3.1-8b, llama3-70b, mixtral-8x7b, gemma2-9b       |
| Max Tokens     | Slider 100–4000                                                         |
| Persist History| Toggle (saves conversation between sessions)                             |
| Theme          | Primer Dark / Primer Light                                              |
| Author Name    | Used in AI Base exports                                                 |

All settings write directly to .env on save and apply immediately.

---

### 7. AI BASE EXPORT

ARIA can package her entire configured identity as a portable, self-contained prompt file usable in any AI tool.

Export formats:
- JSON — Full structured package: metadata, personality, world context, module states, assembled system prompt, quick-start guide
- TXT  — System prompt only (paste directly into Claude.ai, ChatGPT, any API call)

Export contents (JSON):
```json
{
  "aria_base_version": "1.0",
  "metadata": { "name", "description", "author", "tags", "intended_use" },
  "personality": { "base", "tone", "speech_patterns", "never_says" },
  "world_context": { "world_name", "description", "aria_role", "bounds", "limits" },
  "modules": { "ORACLE_MODE": false, "ASSISTANT_MODE": true, "..." },
  "system_prompt": { "full_text": "..." },
  "quick_start": { "model", "max_tokens", "temperature", "usage_note" }
}
```

Import: Load any ARIA base JSON back in — applies world context and module states instantly.

History panel shows all previous exports with date, world name, Re-export and Copy Path buttons.

Exports save to the exports/ folder.

---

### 8. FIRST-RUN SETUP WIZARD

Triggers only on true first launch (no contexts exist). Guides you through 5 questions in a conversational chat style:

  1. "who are you? what do I call you?"      → saves as AUTHOR_NAME in config
  2. "what world am I in right now?"         → world_description
  3. "what's my role here?"                  → aria_role
  4. "what do you need me for?"              → intended_use
  5. "anything I should/shouldn't know?"     → knowledge_bounds

ARIA gives brief bridging responses between questions. Progress bar shows steps. On completion, world context is saved and main window opens. The wizard never appears again — even if you deactivate all contexts.

---

### 9. BOOTSTRAPPER (Windows)

Requires JDK 21 only — everything else is handled automatically.

| File         | What it does                                                                 |
|--------------|------------------------------------------------------------------------------|
| setup.bat    | Checks for JDK (not JRE), creates .env template, creates folders, downloads Maven + all dependencies, compiles and packages the app |
| run.bat      | Checks JDK, auto-builds if first run, launches ARIA                         |
| build.bat    | Clean rebuild for after editing source                                       |
| mvnw.cmd     | Maven Wrapper — no separate Maven install needed                             |

Error messages are in plain language. If you have only the JRE, it tells you exactly what to install and where to get it.

---

## DATA FILES

| File                          | Contains                                    | Max size          |
|-------------------------------|---------------------------------------------|-------------------|
| config/modules.json           | Module on/off states                        | Tiny              |
| config/world_contexts.json    | All world context profiles                  | Grows with use    |
| data/oracle_log.json          | All predictions + outcomes + notes          | Grows with use    |
| data/episodic_memory.json     | World-scoped narrative memory entries       | 60 per world max  |
| data/conversation_history.json| Chat log (only if Persist History is on)    | Session-based     |
| .env                          | API keys + all settings                     | Fixed             |
| exports/                      | All AI Base export files                    | One per export    |

---

## KNOWN LIMITATIONS

| Item               | Detail                                                                 |
|--------------------|------------------------------------------------------------------------|
| Windows only       | Batch scripts are Windows-native. Java/JavaFX runs cross-platform but the bootstrapper does not |
| No streaming       | Responses appear after full completion — no token-by-token streaming   |
| No image input     | Text-only. Vision models not implemented                               |
| No file attachment | No document or file drag-and-drop support                              |
| Single window      | No multi-tab or multi-session support                                  |
| Local only         | No sync, no cloud save, no multi-device access                         |
| Web search depth   | ORACLE uses DuckDuckGo Instant Answer API — good for known topics, limited for very recent events or niche subjects |

---

## STABILITY STATUS (Post-Audit)

| Category                | Count |
|-------------------------|-------|
| Critical bugs fixed     | 3     |
| Medium bugs fixed       | 3     |
| Low bugs fixed          | 2     |
| Features added (ORACLE) | 3 (web search, world context wiring, tuned prompt) |
| Features added (memory) | 2 (episodic memory, prediction log)               |
| Compilation errors      | 0     |
| Build result            | SUCCESS |

JAR: 29MB — fully self-contained, no runtime install beyond JDK 21.

ARIA 1.1.0 is stable and production-ready for personal use.

---

## WHAT IS NEXT (Proposed)

In priority order:

3. Crystallize Mode — ARIA takes a conversation or ORACLE session and outputs a structured design document (GDD for games, framework spec for systems, series bible for stories). Solves the practical problem of ideas living only in chat history.

4. Prompt Mutation Engine — describe what you want a prompt to do, ARIA drafts and explains it, you iterate conversationally, she saves it as a named reusable module. Turns prompt engineering into a first-class feature.

5. ARIA Timeline — every world context, ORACLE prediction, crystallized document, and module displayed as a personal map of how your ideas evolved across time.
