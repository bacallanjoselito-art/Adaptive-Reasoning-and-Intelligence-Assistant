============================================
  ARIA - AI Companion
  Quick Start Guide
============================================


--- FIRST TIME ON A NEW MACHINE ---

  1. Install Java 21 (one-time, only if you don't have it)
     Download: https://adoptium.net/temurin/releases/?version=21

  2. Double-click  ARIA.bat
     This downloads everything else and builds ARIA.
     Takes 2-5 minutes the first time.


--- DAILY USE ---

  Double-click  run.bat   to start ARIA
  Double-click  build.bat to rebuild after editing source files


--- GETTING AN API KEY (FREE) ---

  Groq is free and fast. Sign up here:
    https://console.groq.com

  Click "Create API Key", copy the key (starts with gsk_),
  then either:
    A) Paste it into the .env file:  GROQ_API_KEY=gsk_...
    B) Open ARIA -> Settings -> paste in Groq API Key -> Save


--- FILES YOU CAN EDIT ---

  .env                              API keys and settings
  prompts/aria_system_prompt.txt    ARIA's personality
  config/modules.json               Which modules are on
  config/world_contexts.json        Saved world contexts


--- MODULES ---

  ASSISTANT MODE   Direct, task-focused (default)
  SCHOLAR MODE     Deep reasoning, full thinking visible
  FOOL MODE        Playful, confused, chaotic
  NPC MODE         Stays fully in character as world context
  MATH ENGINE      Step-by-step math working
  MEMORY           Remembers earlier conversation


--- WORLD CONTEXT ---

  Open from sidebar -> World Context.
  Create as many context profiles as you want.
  Activate one to set ARIA's world, role, and knowledge.


--- TROUBLESHOOTING ---

  "Java is not installed"
    -> Install JDK 21 from the link above, restart your terminal.

  "no API keys configured"
    -> Open Settings inside ARIA, paste your Groq key, click Save.

  Build is slow / hangs
    -> First build downloads Maven + all dependencies (~150 MB).
       Wait it out. Future builds are seconds.

  Need to reset everything
    -> Delete the 'target' folder and run setup.bat again.

============================================
