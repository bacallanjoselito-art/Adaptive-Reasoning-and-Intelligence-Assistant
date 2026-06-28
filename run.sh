#!/usr/bin/env bash
# Run ARIA — checks for Java 21+, builds if needed, then launches.

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Java check ──────────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
    echo ""
    echo "  [!] Java is not installed or not in PATH."
    echo ""
    echo "      ARIA requires JDK 21 (LTS). Download the installer here:"
    echo "      https://adoptium.net/temurin/releases/?version=21"
    echo ""
    echo "      After installing, open a new terminal and run ./run.sh again."
    echo ""
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | awk -F[\".] '/version/ {print $2}')
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo ""
    echo "  [!] Java $JAVA_VER found, but ARIA needs Java 17 or later (Java 21 recommended)."
    echo ""
    echo "      Download JDK 21 LTS:"
    echo "      https://adoptium.net/temurin/releases/?version=21"
    echo ""
    exit 1
fi

# ── Build if jar is missing ──────────────────────────────────────────────────
if [ ! -f "target/aria-companion-1.0.0.jar" ]; then
    echo "  Building ARIA (first run — this takes 2-5 minutes while Maven downloads dependencies)..."
    echo ""
    mvn package -q -DskipTests
    echo "  Build complete."
    echo ""
fi

echo "  Starting ARIA..."
mvn javafx:run -q
