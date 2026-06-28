package aria.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Renders math-heavy responses in a WebView using KaTeX.
 * Handles $$...$$, \[...\], $...$, \(...\) delimiters.
 * Math is extracted BEFORE HTML-escaping so LaTeX content is never mangled.
 */
public class MathRenderer {

    // Markers that indicate a response contains math notation
    private static final String[] MATH_MARKERS = {
        "\\frac", "\\sqrt", "\\int", "\\sum", "\\lim", "\\Delta",
        "\\theta", "\\pi", "\\alpha", "\\beta", "\\gamma", "\\lambda",
        "\\infty", "^{", "_{", "\\cdot", "\\times", "\\leq", "\\geq",
        "\\sin", "\\cos", "\\tan", "\\log", "\\ln", "\\vec", "\\hat",
        "\\partial", "\\nabla", "\\forall", "\\exists", "\\in", "\\notin",
        "\\subset", "\\cup", "\\cap", "\\mathbb", "\\mathrm", "\\text{",
        "\\begin{", "\\end{", "\\left", "\\right",
        "$$", "\\[", "\\("
    };

    private static volatile File katexDir = null;

    // ─── PUBLIC API ────────────────────────────────────────────────────────────

    public static boolean containsMath(String text) {
        if (text == null) return false;
        for (String marker : MATH_MARKERS) {
            if (text.contains(marker)) return true;
        }
        // Bare $...$ inline math
        int d = text.indexOf('$');
        return d >= 0 && text.indexOf('$', d + 1) > d + 1;
    }

    public static boolean containsStepByStep(String text) {
        if (text == null) return false;
        return (text.contains("Step 1") || text.contains("step 1"))
            && (text.contains("Step 2") || text.contains("step 2"));
    }

    public static String buildMathHtml(String content, boolean isDark) {
        File kDir       = ensureKatexExtracted();
        String katexJs  = kDir != null ? kDir.toURI() + "katex.min.js"  : null;
        String katexCss = kDir != null ? kDir.toURI() + "katex.min.css" : null;

        // Phase 1 — pull math blocks out before any HTML escaping
        List<String[]> blocks = new ArrayList<>();  // {placeholder, "D"|"I", latex}
        String text = extractMath(content, blocks);

        // Phase 2 — escape what remains
        text = escapeHtml(text);

        // Phase 3 — restore math as KaTeX-ready spans
        for (String[] b : blocks) {
            String tag = b[1].equals("D")
                ? "<span class=\"math-display\">" + b[2] + "</span>"
                : "<span class=\"math-inline\">"  + b[2] + "</span>";
            text = text.replace(b[0], tag);
        }

        // Phase 4 — apply markdown-like text formatting + step cards
        text = applyFormatting(text, isDark);

        return buildDocument(text, isDark, katexCss, katexJs);
    }

    // ─── MATH EXTRACTION ───────────────────────────────────────────────────────

    /**
     * Walk the text character-by-character, pulling out all math delimiters
     * and replacing them with stable placeholders.  The placeholders contain
     * only ASCII letters/digits/underscores so they survive escapeHtml intact.
     */
    private static String extractMath(String src, List<String[]> out) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0, cnt = 0;

        while (i < src.length()) {
            char c = src.charAt(i);

            // $$...$$ — display math
            if (c == '$' && peek(src, i + 1) == '$') {
                int end = src.indexOf("$$", i + 2);
                if (end > i + 2) {
                    String latex = src.substring(i + 2, end).trim();
                    String ph = "ARIAMATH" + (cnt++) + "D";
                    out.add(new String[]{ph, "D", latex});
                    sb.append(ph);
                    i = end + 2;
                    continue;
                }
            }

            // \[...\] — display math
            if (c == '\\' && peek(src, i + 1) == '[') {
                int end = src.indexOf("\\]", i + 2);
                if (end > i + 2) {
                    String latex = src.substring(i + 2, end).trim();
                    String ph = "ARIAMATH" + (cnt++) + "D";
                    out.add(new String[]{ph, "D", latex});
                    sb.append(ph);
                    i = end + 2;
                    continue;
                }
            }

            // \(...\) — inline math
            if (c == '\\' && peek(src, i + 1) == '(') {
                int end = src.indexOf("\\)", i + 2);
                if (end > i + 2) {
                    String latex = src.substring(i + 2, end).trim();
                    String ph = "ARIAMATH" + (cnt++) + "I";
                    out.add(new String[]{ph, "I", latex});
                    sb.append(ph);
                    i = end + 2;
                    continue;
                }
            }

            // $...$ — inline math (not part of $$)
            if (c == '$' && peek(src, i + 1) != '$') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '$' && src.charAt(j) != '\n') j++;
                if (j < src.length() && src.charAt(j) == '$' && j > i + 1) {
                    String latex = src.substring(i + 1, j).trim();
                    if (!latex.isEmpty()) {
                        String ph = "ARIAMATH" + (cnt++) + "I";
                        out.add(new String[]{ph, "I", latex});
                        sb.append(ph);
                        i = j + 1;
                        continue;
                    }
                }
            }

            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static char peek(String s, int idx) {
        return idx < s.length() ? s.charAt(idx) : '\0';
    }

    // ─── TEXT FORMATTING ───────────────────────────────────────────────────────

    private static final Pattern STEP_PAT = Pattern.compile(
        "(?m)^(Step|STEP)\\s+(\\d+)[:.\\)]\\s*");

    private static String applyFormatting(String html, boolean isDark) {
        String accentCol = isDark ? "#3fb950" : "#2563eb";
        String stepBg    = isDark ? "#22272e" : "#f0f4f8";
        String borderCol = isDark ? "#30363d" : "#d0d7de";
        String textColor = isDark ? "#e2e8f0" : "#1a202c";

        // Process line by line to handle step cards
        String[] lines = html.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inStep = false;

        for (String line : lines) {
            Matcher m = STEP_PAT.matcher(line);
            if (m.find()) {
                if (inStep) sb.append("</div>\n");
                String stepNum = m.group(2);
                String rest    = line.substring(m.end());
                sb.append("<div class=\"step-card\">")
                  .append("<div class=\"step-num\">STEP ").append(stepNum).append("</div>")
                  .append(rest).append("<br>");
                inStep = true;
            } else if (inStep && line.isBlank()) {
                sb.append("</div>\n");
                inStep = false;
            } else {
                sb.append(line).append("<br>");
            }
        }
        if (inStep) sb.append("</div>\n");

        html = sb.toString();

        // Final answer / Therefore box
        html = html.replaceAll(
            "(?i)(Therefore[,:]?|Final [Aa]nswer[,:]?|∴)\\s+",
            "<div class=\"final-box\">$1 ");
        if (html.contains("<div class=\"final-box\">")) {
            // close the box at the next <br> after it opens
            html = html.replaceFirst("(<div class=\"final-box\">[^<]*(?:<(?!br)[^<]*)*)<br>",
                "$1</div><br>");
        }

        // Inline markdown: **bold**, *italic*, `code`
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__",         "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*",        "<em>$1</em>");
        html = html.replaceAll("`([^`]+)`",
            "<code style=\"background:" + (isDark ? "#161b22" : "#f0f4f8") +
            ";color:" + (isDark ? "#79c0ff" : "#0550ae") +
            ";padding:1px 5px;border-radius:4px;font-size:13px\">$1</code>");

        // Headings (## and #)
        html = html.replaceAll("(?m)^## (.+?)<br>",
            "<h3 style=\"margin:10px 0 4px;color:" + textColor + "\">$1</h3>");
        html = html.replaceAll("(?m)^# (.+?)<br>",
            "<h2 style=\"margin:12px 0 4px;color:" + textColor + "\">$1</h2>");

        // Bullet lists
        html = html.replaceAll("(?m)^[-*] (.+?)<br>",
            "<li style=\"margin:2px 0\">$1</li>");

        return html;
    }

    // ─── HTML DOCUMENT BUILDER ─────────────────────────────────────────────────

    private static String buildDocument(String body, boolean isDark,
                                        String katexCss, String katexJs) {
        String bg       = isDark ? "#1c2128" : "#ffffff";
        String fg       = isDark ? "#e2e8f0" : "#1a202c";
        String accent   = isDark ? "#3fb950" : "#2563eb";
        String stepBg   = isDark ? "#22272e" : "#f0f4f8";
        String finalBg  = isDark ? accent + "18" : "#eff6ff";
        String finalBdr = isDark ? accent : "#3b82f6";
        String numCol   = isDark ? "#8b949e" : "#6e7781";
        String codeBg   = isDark ? "#161b22" : "#f6f8fa";

        StringBuilder head = new StringBuilder("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n");
        if (katexCss != null)
            head.append("<link rel=\"stylesheet\" href=\"").append(katexCss).append("\">\n");
        if (katexJs != null)
            head.append("<script src=\"").append(katexJs).append("\"></script>\n");

        head.append("<style>\n")
            .append("* { box-sizing: border-box; margin: 0; padding: 0; }\n")
            .append("body {\n")
            .append("  background: ").append(bg).append(";\n")
            .append("  color: ").append(fg).append(";\n")
            .append("  font-family: 'Segoe UI', 'Inter', system-ui, sans-serif;\n")
            .append("  font-size: 14px; line-height: 1.7;\n")
            .append("  padding: 12px 16px; word-wrap: break-word;\n")
            .append("}\n")
            // Display math — centered with equation number
            .append(".math-block-wrap {\n")
            .append("  display: flex; align-items: center;\n")
            .append("  margin: 14px 0; gap: 12px;\n")
            .append("}\n")
            .append(".math-block-wrap .katex-display { flex: 1; margin: 0; overflow-x: auto; }\n")
            .append(".eq-num {\n")
            .append("  color: ").append(numCol).append(";\n")
            .append("  font-size: 12px; min-width: 32px; text-align: right;\n")
            .append("  font-family: 'Consolas', monospace;\n")
            .append("}\n")
            // Inline math
            .append(".katex { font-size: 1.05em; }\n")
            // Step cards
            .append(".step-card {\n")
            .append("  background: ").append(stepBg).append(";\n")
            .append("  border-left: 3px solid ").append(accent).append(";\n")
            .append("  border-radius: 0 6px 6px 0;\n")
            .append("  padding: 10px 14px;\n")
            .append("  margin: 8px 0;\n")
            .append("}\n")
            .append(".step-num {\n")
            .append("  color: ").append(accent).append(";\n")
            .append("  font-size: 11px; font-weight: bold;\n")
            .append("  letter-spacing: 0.05em; margin-bottom: 6px;\n")
            .append("}\n")
            // Final answer box
            .append(".final-box {\n")
            .append("  background: ").append(finalBg).append(";\n")
            .append("  border: 1.5px solid ").append(finalBdr).append(";\n")
            .append("  border-radius: 8px; padding: 12px 16px;\n")
            .append("  margin: 12px 0; font-size: 15px; font-weight: 600;\n")
            .append("}\n")
            // Inline code
            .append("code { font-family: 'Consolas', 'Courier New', monospace; }\n")
            // Headings
            .append("h2, h3 { font-weight: 600; }\n")
            // List
            .append("li { margin-left: 18px; }\n")
            .append("</style>\n</head>\n<body>\n")
            .append("<div id=\"content\">").append(body).append("</div>\n");

        // JavaScript: render math spans, wrap display math, add eq numbers
        if (katexJs != null) {
            head.append("<script>\n")
                .append("(function() {\n")
                .append("  var eqCount = 0;\n")
                // Render display math
                .append("  document.querySelectorAll('.math-display').forEach(function(el) {\n")
                .append("    try {\n")
                .append("      var wrap = document.createElement('div');\n")
                .append("      wrap.className = 'math-block-wrap';\n")
                .append("      var inner = document.createElement('div');\n")
                .append("      katex.render(el.textContent, inner, {throwOnError:false, displayMode:true});\n")
                .append("      var num = document.createElement('span');\n")
                .append("      num.className = 'eq-num';\n")
                .append("      eqCount++;\n")
                .append("      num.textContent = '(' + eqCount + ')';\n")
                .append("      wrap.appendChild(inner);\n")
                .append("      wrap.appendChild(num);\n")
                .append("      el.parentNode.replaceChild(wrap, el);\n")
                .append("    } catch(e) {}\n")
                .append("  });\n")
                // Render inline math
                .append("  document.querySelectorAll('.math-inline').forEach(function(el) {\n")
                .append("    try {\n")
                .append("      katex.render(el.textContent, el, {throwOnError:false, displayMode:false});\n")
                .append("    } catch(e) {}\n")
                .append("  });\n")
                .append("})();\n")
                .append("</script>\n");
        }

        head.append("</body>\n</html>");
        return head.toString();
    }

    // ─── HTML ESCAPING ─────────────────────────────────────────────────────────

    private static String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    // ─── KATEX EXTRACTION ──────────────────────────────────────────────────────

    private static synchronized File ensureKatexExtracted() {
        if (katexDir != null && katexDir.exists()) return katexDir;
        try {
            Path tmp = Files.createTempDirectory("aria-katex-");
            extractResource("/katex/katex.min.js",  new File(tmp.toFile(), "katex.min.js"));
            extractResource("/katex/katex.min.css", new File(tmp.toFile(), "katex.min.css"));
            katexDir = tmp.toFile();
            tmp.toFile().deleteOnExit();
            return katexDir;
        } catch (Exception e) {
            System.err.println("[MathRenderer] KaTeX extraction failed: " + e.getMessage());
            return null;
        }
    }

    private static void extractResource(String resource, File dest) throws Exception {
        try (InputStream in = MathRenderer.class.getResourceAsStream(resource)) {
            if (in == null) throw new FileNotFoundException("Not found: " + resource);
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
