package aria.core;

import java.io.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class TtsManager {

    public enum Availability { UNAVAILABLE, KOKORO_PYTHON, OLLAMA_KOKORO }

    private Availability availability = Availability.UNAVAILABLE;
    private volatile boolean enabled = false;
    private volatile double speed = 1.0;
    private volatile String style = "neutral";

    private final AtomicReference<Process> currentProcess = new AtomicReference<>(null);

    public TtsManager() {
        detectAvailability();
    }

    private void detectAvailability() {
        if (checkKokoroPython()) {
            availability = Availability.KOKORO_PYTHON;
            return;
        }
        if (checkOllamaKokoro()) {
            availability = Availability.OLLAMA_KOKORO;
            return;
        }
        availability = Availability.UNAVAILABLE;
    }

    private boolean checkKokoroPython() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "python", "-c", "import kokoro; print('ok')"
            });
            p.waitFor();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return "ok".equals(out);
        } catch (Exception e) { return false; }
    }

    private boolean checkOllamaKokoro() {
        try {
            java.net.URL url = new java.net.URL("http://localhost:11434/api/tags");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(500);
            conn.setReadTimeout(1000);
            conn.connect();
            String body = new String(conn.getInputStream().readAllBytes());
            return body.toLowerCase(Locale.ROOT).contains("kokoro");
        } catch (Exception e) { return false; }
    }

    public boolean isAvailable() { return availability != Availability.UNAVAILABLE; }
    public Availability getAvailability() { return availability; }
    public boolean isEnabled() { return enabled && isAvailable(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setSpeed(double speed) { this.speed = Math.max(0.5, Math.min(2.0, speed)); }
    public void setStyle(String style) { this.style = style; }

    public void speak(String text) {
        if (!isEnabled() || text == null || text.isBlank()) return;
        String clean = cleanForSpeech(text);
        if (clean.isBlank()) return;

        stopCurrent();

        Thread thread = new Thread(() -> {
            try {
                Process proc = null;
                if (availability == Availability.KOKORO_PYTHON) {
                    proc = speakViaPython(clean);
                } else if (availability == Availability.OLLAMA_KOKORO) {
                    proc = speakViaOllama(clean);
                }
                if (proc != null) {
                    currentProcess.set(proc);
                    proc.waitFor();
                    currentProcess.set(null);
                }
            } catch (Exception e) {
                System.err.println("[TTS] speak error (non-fatal): " + e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() { stopCurrent(); }

    private void stopCurrent() {
        Process p = currentProcess.getAndSet(null);
        if (p != null && p.isAlive()) {
            p.destroy();
        }
    }

    private Process speakViaPython(String text) throws Exception {
        String script = String.format(
            "import kokoro, sounddevice as sd, numpy as np\n" +
            "pipeline = kokoro.KPipeline(lang_code='a')\n" +
            "samples = []\n" +
            "for _, _, audio in pipeline('%s', speed=%.1f):\n" +
            "    samples.append(audio)\n" +
            "if samples:\n" +
            "    audio = np.concatenate(samples)\n" +
            "    sd.play(audio, samplerate=24000)\n" +
            "    sd.wait()\n",
            escapeForPython(text), speed
        );
        ProcessBuilder pb = new ProcessBuilder("python", "-c", script);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private Process speakViaOllama(String text) throws Exception {
        return null;
    }

    private String cleanForSpeech(String text) {
        return text
            .replaceAll("\\\\[a-zA-Z]+\\{[^}]*\\}", " ")
            .replaceAll("\\$+[^$]+\\$+", " ")
            .replaceAll("```[\\s\\S]*?```", " ")
            .replaceAll("`[^`]+`", " ")
            .replaceAll("[\\[\\]\\{\\}\\*#_~]", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    private String escapeForPython(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ");
    }

    public String speakOracleVerdict(String verdict, String confidenceLabel) {
        String speech = "Based on current patterns, " + verdict
            + ". Confidence: " + confidenceLabel + ".";
        speak(speech);
        return speech;
    }
}
