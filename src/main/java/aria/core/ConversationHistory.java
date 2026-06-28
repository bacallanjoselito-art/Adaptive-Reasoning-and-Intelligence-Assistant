package aria.core;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class ConversationHistory {

    public static class Message {
        public String role;
        public String content;
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private final List<Message> history = new ArrayList<>();
    private final boolean persist;

    public ConversationHistory(boolean persist) {
        this.persist = persist;
        if (persist) {
            loadFromFile();
        }
    }

    public void addUser(String content) {
        history.add(new Message("user", content));
    }

    public void addAssistant(String content) {
        history.add(new Message("assistant", content));
    }

    public List<Message> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void clear() {
        history.clear();
    }

    private File resolveFile() {
        File primary = new File("data/conversation_history.json");
        File alt = new File("aria/data/conversation_history.json");
        if (alt.exists() && !primary.exists()) return alt;
        return primary;
    }

    public void loadFromFile() {
        File f = resolveFile();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Message>>() {}.getType();
            List<Message> loaded = gson.fromJson(r, type);
            if (loaded != null) {
                history.addAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveToFile() {
        File f = resolveFile();
        try {
            f.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = new FileWriter(f)) {
                gson.toJson(history, w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int size() {
        return history.size();
    }
}
