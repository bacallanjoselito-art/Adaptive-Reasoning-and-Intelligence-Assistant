package aria.core;

import okhttp3.*;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ImageClient {

    private static final String BASE_URL = "https://image.pollinations.ai/prompt/";
    private static final String[] IMAGE_TRIGGERS = {
        "generate image", "create image", "draw ", "visualize", "show me"
    };

    private final OkHttpClient httpClient;

    public ImageClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public boolean isImageRequest(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        for (String trigger : IMAGE_TRIGGERS) {
            if (lower.contains(trigger)) return true;
        }
        return false;
    }

    public String extractPrompt(String message) {
        if (message == null) return "";
        String lower = message.toLowerCase(Locale.ROOT);
        String result = message;
        for (String trigger : new String[]{
            "generate image of", "generate image", "create image of", "create image",
            "draw a ", "draw an ", "draw ", "visualize ", "show me a ", "show me an ", "show me "
        }) {
            int idx = lower.indexOf(trigger);
            if (idx >= 0) {
                result = message.substring(idx + trigger.length()).trim();
                break;
            }
        }
        return result.isEmpty() ? message : result;
    }

    public byte[] fetchImage(String prompt) {
        try {
            String encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
                .replace("+", "%20");
            String url = BASE_URL + encoded;
            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "ARIA-Companion/1.1")
                .get()
                .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                String ct = response.header("Content-Type", "");
                if (ct != null && !ct.startsWith("image/")) return null;
                return response.body().bytes();
            }
        } catch (Exception e) {
            System.err.println("[ImageClient] fetch failed (non-fatal): " + e.getMessage());
            return null;
        }
    }
}
