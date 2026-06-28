package aria;

import aria.core.*;
import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class StressTest {

    static int passed = 0;
    static int failed = 0;
    static final List<String> failures = new ArrayList<>();
    static File TEST_DIR;

    public static void main(String[] args) throws Exception {
        System.out.println("\n══════════════════════════════════════════════════════");
        System.out.println("  ARIA STRESS TEST");
        System.out.println("══════════════════════════════════════════════════════\n");

        setupTestDir();

        runSection("OracleLog — data layer", () -> {
            testOracleLogBasic();
            testOracleLogPersistence();
            testOracleLogResolveNonExistent();
            testOracleLogConcurrentWrites();
            testOracleLogAccuracyMath();
            testOracleLogAccuracyByDomain();
            testOracleLogCorruptFile();
            testOracleLogNullDomains();
            testOracleLogEmptyStringFields();
            testOracleLogGetAllReversed();
            testOracleLogOpenVsResolved();
        });

        runSection("EpisodicMemory — data layer", () -> {
            testEpisodicMemoryBasic();
            testEpisodicMemoryTrimAt60();
            testEpisodicMemoryNullWorld();
            testEpisodicMemoryEmptyWorld();
            testEpisodicMemoryBlankWorld();
            testEpisodicMemoryBuildPromptBlockEmpty();
            testEpisodicMemoryBuildPromptBlockContent();
            testEpisodicMemoryBuildPromptBlockMax10Injected();
            testEpisodicMemoryDeleteEntry();
            testEpisodicMemoryDeleteOutOfBounds();
            testEpisodicMemoryDeleteNegativeIndex();
            testEpisodicMemoryPersistence();
            testEpisodicMemoryConcurrentWrites();
            testEpisodicMemoryCorruptFile();
            testEpisodicMemoryMultipleWorlds();
            testEpisodicMemorySpecialCharsInWorldName();
            testEpisodicMemoryVeryLongSummary();
        });

        runSection("ConfigStore", () -> {
            testConfigStoreDefaults();
            testConfigStoreSetGet();
            testConfigStoreMissingKey();
            testConfigStorePlaceholderValue();
            testConfigStoreEmptyStringValue();
        });

        runSection("ConversationHistory", () -> {
            testConversationHistoryAddRetrieve();
            testConversationHistoryRoles();
            testConversationHistoryClear();
            testConversationHistoryEmpty();
            testConversationHistoryVeryLongMessage();
            testConversationHistory10kMessages();
        });

        runSection("ModuleManager", () -> {
            // Back up real modules.json so these tests don't corrupt it
            File modFile = new File("aria/config/modules.json");
            if (!modFile.exists()) modFile = new File("config/modules.json");
            String backup = modFile.exists() ? Files.readString(modFile.toPath()) : null;
            final File modFileFinal = modFile;
            try {
                resetModuleDefaults();
                testModuleManagerDefaults();
                testModuleManagerAllOn();
                testModuleManagerAllOff();
                testModuleManagerOraclePromptHasJson();
                testModuleManagerSaveLoad();
                testModuleManagerGetActiveModeLabel();
                testModuleManagerIsOracleActive();
            } finally {
                // Restore original modules.json
                resetModuleDefaults();
                if (backup != null) Files.writeString(modFileFinal.toPath(), backup);
                else modFileFinal.delete();
            }
        });

        runSection("OracleLog + EpisodicMemory integration", () -> {
            testIntegrationPredictionToMemory();
            testIntegrationOutcomeClosesLoop();
            testIntegration100PredictionsAccuracy();
        });

        runSection("LLMClient.searchWeb (network-optional)", () -> {
            testSearchWebSpecialChars();
            testSearchWebEmptyQuery();
            testSearchWebLongQuery();
            testSearchWebUnicodeQuery();
        });

        runSection("Edge cases & regressions", () -> {
            testOracleLogResolveAlreadyResolved();
            testEpisodicMemoryTrimKeepsMostRecent();
            testOracleLogAllOutcomeTypes();
            testMemoryPromptBlockOnlyInjectsRecent10();
        });

        runSection("v1.3 — NPC Mode identity replacement", () -> {
            check("NPC prompt contains NO ARIA identity", StressTest::testNpcPromptNoAriaIdentity);
            check("NPC prompt contains world description",  StressTest::testNpcPromptContainsWorldDesc);
            check("NPC prompt has AI-awareness rules",      StressTest::testNpcPromptForbidsAIAwareness);
            check("NPC OFF uses normal ARIA prompt path",   StressTest::testNpcModeOffUsesAriaPrompt);
            check("NPC with no world falls back to normal", StressTest::testNpcModeNoWorldFallsBackToNormal);
            check("NPC prompt build: 200x < 3 seconds",    StressTest::testNpcPromptBuildPerformance);
        });

        runSection("v1.3 — Access code & locked memory", () -> {
            check("correct access code unlocks memory",     StressTest::testAccessCodeDetection);
            check("wrong code does NOT unlock memory",      StressTest::testAccessCodeWrongInput);
            check("empty string does NOT unlock",           StressTest::testAccessCodeEmptyInput);
            check("null input does NOT unlock",             StressTest::testAccessCodeNullInput);
            check("locked memory absent from normal prompt",StressTest::testLockedMemoryNotInNormalPrompt);
            check("locked memory present after unlock",     StressTest::testLockedMemoryAppearsWhenUnlocked);
        });

        runSection("v1.3 — MarkdownRenderer", () -> {
            check("detects **bold**",                       StressTest::testMarkdownDetectionBold);
            check("detects ```code block```",               StressTest::testMarkdownDetectionCodeBlock);
            check("detects # heading",                      StressTest::testMarkdownDetectionHeading);
            check("detects __underline bold__",             StressTest::testMarkdownDetectionUnderscoreBold);
            check("plain text NOT detected as markdown",    StressTest::testMarkdownDetectionPlainText);
            check("null returns false (no NPE)",            StressTest::testMarkdownDetectionNull);
            check("render() returns non-null VBox",         StressTest::testMarkdownRenderReturnVBox);
            check("render() handles code blocks",           StressTest::testMarkdownRenderCodeBlock);
            check("render() handles empty string",          StressTest::testMarkdownRenderEmpty);
        });

        printSummary();
        cleanupTestDir();
        System.exit(failed > 0 ? 1 : 0);
    }

    // ══════════════════════════════════════════════════════
    //  ORACLE LOG
    // ══════════════════════════════════════════════════════

    static void testOracleLogBasic() {
        check("add entry — id non-empty, counts correct", () -> {
            OracleLog log = freshLog();
            String id = log.addEntry("Rune Catchers", "meta shift", "fire wins",
                78, "High", "2 weeks", list("game-design", "balance"));
            assertThat(!id.isEmpty(), "id should not be empty");
            assertThat(log.totalCount() == 1, "total=1");
            assertThat(log.openCount() == 1, "open=1");
            assertThat(log.resolvedCount() == 0, "resolved=0");
            OracleLog.Entry e = log.findById(id).orElseThrow();
            assertThat("Rune Catchers".equals(e.worldContext), "worldContext matches");
            assertThat(e.confidence == 78, "confidence=78");
            assertThat(!e.isResolved(), "not resolved");
        });
    }

    static void testOracleLogPersistence() {
        check("save → reload preserves all entries", () -> {
            OracleLog log1 = freshLog();
            log1.addEntry("World A", "s1", "v1", 60, "Moderate", "1 month", list("tech"));
            log1.addEntry("World B", "s2", "v2", 40, "Low", "1 week", list("finance"));
            OracleLog log2 = reloadLog();
            assertThat(log2.totalCount() == 2, "reloaded 2 entries, got " + log2.totalCount());
            assertThat(log2.openCount() == 2, "both open");
        });
    }

    static void testOracleLogResolveNonExistent() {
        check("resolve non-existent id returns false", () -> {
            OracleLog log = freshLog();
            boolean r = log.resolveEntry("ghost-id", OracleLog.OUTCOME_CORRECT, "note");
            assertThat(!r, "should return false");
        });
    }

    static void testOracleLogConcurrentWrites() {
        check("50 concurrent addEntry calls — no crash, all stored", () -> {
            OracleLog log = freshLog();
            ExecutorService ex = Executors.newFixedThreadPool(10);
            AtomicInteger errors = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(50);
            for (int i = 0; i < 50; i++) {
                final int n = i;
                ex.submit(() -> {
                    try {
                        log.addEntry("World", "subject " + n, "verdict",
                            50 + (n % 40), "Moderate", "1w", list("tag" + (n % 5)));
                    } catch (Exception e) { errors.incrementAndGet(); }
                    finally { latch.countDown(); }
                });
            }
            latch.await(10, TimeUnit.SECONDS);
            ex.shutdown();
            assertThat(errors.get() == 0, errors.get() + " errors during concurrent writes");
            assertThat(log.totalCount() == 50, "expected 50, got " + log.totalCount());
        });
    }

    static void testOracleLogAccuracyMath() {
        check("overall accuracy: 2 correct + 1 partial + 1 incorrect = 3/4", () -> {
            OracleLog log = freshLog();
            String a = log.addEntry("W", "s1", "v", 80, "H", "1w", list("tech"));
            String b = log.addEntry("W", "s2", "v", 60, "M", "1w", list("tech"));
            String c = log.addEntry("W", "s3", "v", 40, "L", "1w", list("finance"));
            String d = log.addEntry("W", "s4", "v", 30, "L", "1w", list("finance"));
            log.resolveEntry(a, OracleLog.OUTCOME_CORRECT,   "");
            log.resolveEntry(b, OracleLog.OUTCOME_PARTIAL,   "");
            log.resolveEntry(c, OracleLog.OUTCOME_INCORRECT, "");
            // d remains open
            int[] acc = log.getOverallAccuracy();
            assertThat(acc[1] == 3, "total resolved=3, got " + acc[1]);
            assertThat(acc[0] == 2, "correct+partial=2, got " + acc[0]);
        });
    }

    static void testOracleLogAccuracyByDomain() {
        check("accuracy by domain calculated correctly", () -> {
            OracleLog log = freshLog();
            String a = log.addEntry("W", "s1", "v", 80, "H", "1w", list("tech", "ai"));
            String b = log.addEntry("W", "s2", "v", 60, "M", "1w", list("tech"));
            String c = log.addEntry("W", "s3", "v", 40, "L", "1w", list("finance"));
            log.resolveEntry(a, OracleLog.OUTCOME_CORRECT,   "");
            log.resolveEntry(b, OracleLog.OUTCOME_INCORRECT, "");
            log.resolveEntry(c, OracleLog.OUTCOME_CORRECT,   "");
            Map<String, int[]> byDomain = log.getAccuracyByDomain();
            assertThat(byDomain.containsKey("tech"),    "tech domain present");
            assertThat(byDomain.get("tech")[1] == 2,    "tech total=2");
            assertThat(byDomain.get("tech")[0] == 1,    "tech correct=1 (a correct, b incorrect)");
            assertThat(byDomain.containsKey("ai"),      "ai domain present");
            assertThat(byDomain.get("ai")[0] == 1,      "ai correct=1");
            assertThat(byDomain.containsKey("finance"), "finance domain present");
            assertThat(byDomain.get("finance")[0] == 1, "finance correct=1");
        });
    }

    static void testOracleLogCorruptFile() {
        check("corrupt JSON file → empty log, no crash", () -> {
            writeFile(logFile(), "{ this is NOT json }}}}}}");
            OracleLog log = reloadLog();
            assertThat(log.totalCount() == 0, "should be empty after corrupt file");
        });
    }

    static void testOracleLogNullDomains() {
        check("null domains → stored as empty list", () -> {
            OracleLog log = freshLog();
            String id = log.addEntry("W", "s", "v", 50, "M", "1w", null);
            OracleLog.Entry e = log.findById(id).orElseThrow();
            assertThat(e.domains != null, "domains not null");
            assertThat(e.domains.isEmpty(), "domains is empty list");
        });
    }

    static void testOracleLogEmptyStringFields() {
        check("empty string fields stored and retrieved correctly", () -> {
            OracleLog log = freshLog();
            String id = log.addEntry("", "", "", 0, "", "", list());
            OracleLog.Entry e = log.findById(id).orElseThrow();
            assertThat("".equals(e.subject), "subject empty");
            assertThat("".equals(e.verdict), "verdict empty");
        });
    }

    static void testOracleLogGetAllReversed() {
        check("getAll() returns newest first", () -> {
            OracleLog log = freshLog();
            log.addEntry("W", "first",  "v", 50, "M", "1w", list());
            log.addEntry("W", "second", "v", 50, "M", "1w", list());
            log.addEntry("W", "third",  "v", 50, "M", "1w", list());
            List<OracleLog.Entry> all = log.getAll();
            assertThat("third".equals(all.get(0).subject), "newest first: " + all.get(0).subject);
            assertThat("first".equals(all.get(2).subject), "oldest last: " + all.get(2).subject);
        });
    }

    static void testOracleLogOpenVsResolved() {
        check("open/resolved lists are disjoint and complete", () -> {
            OracleLog log = freshLog();
            String a = log.addEntry("W", "s1", "v", 70, "H", "1w", list());
            String b = log.addEntry("W", "s2", "v", 70, "H", "1w", list());
            String c = log.addEntry("W", "s3", "v", 70, "H", "1w", list());
            log.resolveEntry(a, OracleLog.OUTCOME_CORRECT, "");
            log.resolveEntry(b, OracleLog.OUTCOME_INCORRECT, "");
            assertThat(log.openCount() == 1, "1 open (c)");
            assertThat(log.resolvedCount() == 2, "2 resolved (a,b)");
            assertThat(log.totalCount() == 3, "3 total");
        });
    }

    static void testOracleLogResolveAlreadyResolved() {
        check("resolving an already-resolved entry overwrites — no crash", () -> {
            OracleLog log = freshLog();
            String id = log.addEntry("W", "s", "v", 70, "H", "1w", list());
            log.resolveEntry(id, OracleLog.OUTCOME_CORRECT, "first");
            log.resolveEntry(id, OracleLog.OUTCOME_INCORRECT, "overwritten");
            OracleLog.Entry e = log.findById(id).orElseThrow();
            assertThat(OracleLog.OUTCOME_INCORRECT.equals(e.outcome), "outcome overwritten to incorrect");
            assertThat("overwritten".equals(e.outcomeNote), "note overwritten");
        });
    }

    static void testOracleLogAllOutcomeTypes() {
        check("all three outcome types affect accuracy correctly", () -> {
            OracleLog log = freshLog();
            String c = log.addEntry("W", "s1", "v", 70, "H", "1w", list("x"));
            String p = log.addEntry("W", "s2", "v", 70, "H", "1w", list("x"));
            String i = log.addEntry("W", "s3", "v", 70, "H", "1w", list("x"));
            log.resolveEntry(c, OracleLog.OUTCOME_CORRECT,   "");
            log.resolveEntry(p, OracleLog.OUTCOME_PARTIAL,   "");
            log.resolveEntry(i, OracleLog.OUTCOME_INCORRECT, "");
            int[] acc = log.getOverallAccuracy();
            assertThat(acc[0] == 2, "correct+partial=2, got " + acc[0]);
            assertThat(acc[1] == 3, "total=3");
        });
    }

    // ══════════════════════════════════════════════════════
    //  EPISODIC MEMORY
    // ══════════════════════════════════════════════════════

    static void testEpisodicMemoryBasic() {
        check("add and retrieve entries for a world", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("Rune Catchers", EpisodicMemory.TYPE_PREDICTION, "ORACLE said X");
            mem.addEntry("Rune Catchers", EpisodicMemory.TYPE_OUTCOME,    "X happened");
            List<EpisodicMemory.MemoryEntry> entries = mem.getForWorld("Rune Catchers");
            assertThat(entries.size() == 2, "2 entries, got " + entries.size());
        });
    }

    static void testEpisodicMemoryTrimAt60() {
        check("80 entries → trimmed to 60", () -> {
            EpisodicMemory mem = freshMem();
            for (int i = 0; i < 80; i++) mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "Entry " + i);
            assertThat(mem.countForWorld("W") == 60, "trimmed to 60, got " + mem.countForWorld("W"));
        });
    }

    static void testEpisodicMemoryNullWorld() {
        check("null worldContext defaults to 'Real World'", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry(null, EpisodicMemory.TYPE_NOTE, "null world");
            assertThat(mem.countForWorld("Real World") == 1, "stored under Real World");
        });
    }

    static void testEpisodicMemoryEmptyWorld() {
        check("empty string worldContext defaults to 'Real World'", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("", EpisodicMemory.TYPE_NOTE, "empty world");
            assertThat(mem.countForWorld("Real World") == 1, "stored under Real World");
        });
    }

    static void testEpisodicMemoryBlankWorld() {
        check("whitespace-only worldContext defaults to 'Real World'", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("   ", EpisodicMemory.TYPE_NOTE, "blank world");
            assertThat(mem.countForWorld("Real World") == 1, "stored under Real World");
        });
    }

    static void testEpisodicMemoryBuildPromptBlockEmpty() {
        check("buildPromptBlock for unknown world returns empty string", () -> {
            EpisodicMemory mem = freshMem();
            String block = mem.buildPromptBlock("NonExistentWorld");
            assertThat(block.isEmpty(), "should be empty, got: " + block);
        });
    }

    static void testEpisodicMemoryBuildPromptBlockContent() {
        check("buildPromptBlock contains header and entries", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("TestWorld", EpisodicMemory.TYPE_PREDICTION, "ORACLE predicted X");
            mem.addEntry("TestWorld", EpisodicMemory.TYPE_OUTCOME, "X happened");
            String block = mem.buildPromptBlock("TestWorld");
            assertThat(block.contains("EPISODIC MEMORY"), "has header");
            assertThat(block.contains("[PREDICTION]"), "has PREDICTION type");
            assertThat(block.contains("[OUTCOME]"), "has OUTCOME type");
            assertThat(block.contains("ORACLE predicted X"), "has summary text");
        });
    }

    static void testEpisodicMemoryBuildPromptBlockMax10Injected() {
        check("buildPromptBlock injects at most 10 entries even with 60", () -> {
            EpisodicMemory mem = freshMem();
            for (int i = 0; i < 60; i++) mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "Entry " + i);
            String block = mem.buildPromptBlock("W");
            long lineCount = block.lines().filter(l -> l.trim().startsWith("[")).count();
            assertThat(lineCount <= 10, "should inject ≤10 lines, injected " + lineCount);
            assertThat(block.contains("Entry 59"), "most recent entry should be present");
        });
    }

    static void testEpisodicMemoryDeleteEntry() {
        check("deleteEntry removes the most recent entry (reversed index 0)", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "oldest");
            mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "middle");
            mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "newest");
            mem.deleteEntry("W", 0);
            List<EpisodicMemory.MemoryEntry> entries = mem.getForWorld("W");
            assertThat(entries.size() == 2, "2 remain, got " + entries.size());
            assertThat(entries.stream().noneMatch(e -> "newest".equals(e.summary)),
                "newest should be deleted (index 0 in reversed view = last added)");
        });
    }

    static void testEpisodicMemoryDeleteOutOfBounds() {
        check("deleteEntry with out-of-bounds index is a no-op", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "only entry");
            mem.deleteEntry("W", 999);
            assertThat(mem.countForWorld("W") == 1, "still 1 entry");
        });
    }

    static void testEpisodicMemoryDeleteNegativeIndex() {
        check("deleteEntry with negative index is a no-op", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "entry");
            mem.deleteEntry("W", -1);
            assertThat(mem.countForWorld("W") == 1, "still 1 entry");
        });
    }

    static void testEpisodicMemoryPersistence() {
        check("save → reload preserves all worlds and entries", () -> {
            EpisodicMemory mem1 = freshMem();
            mem1.addEntry("PersistWorld", EpisodicMemory.TYPE_PREDICTION, "Will it work?");
            mem1.addEntry("PersistWorld", EpisodicMemory.TYPE_OUTCOME,    "Yes.");
            mem1.addEntry("OtherWorld",   EpisodicMemory.TYPE_NOTE,       "Separate world");
            EpisodicMemory mem2 = reloadMem();
            assertThat(mem2.countForWorld("PersistWorld") == 2, "PersistWorld has 2");
            assertThat(mem2.countForWorld("OtherWorld") == 1, "OtherWorld has 1");
            assertThat(mem2.totalCount() == 3, "total=3");
        });
    }

    static void testEpisodicMemoryConcurrentWrites() {
        check("40 concurrent addEntry calls across 4 worlds — no crash", () -> {
            EpisodicMemory mem = freshMem();
            ExecutorService ex = Executors.newFixedThreadPool(8);
            AtomicInteger errors = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(40);
            for (int i = 0; i < 40; i++) {
                final int n = i;
                ex.submit(() -> {
                    try { mem.addEntry("World" + (n % 4), EpisodicMemory.TYPE_NOTE, "entry " + n); }
                    catch (Exception e) { errors.incrementAndGet(); }
                    finally { latch.countDown(); }
                });
            }
            latch.await(10, TimeUnit.SECONDS);
            ex.shutdown();
            assertThat(errors.get() == 0, errors.get() + " errors");
        });
    }

    static void testEpisodicMemoryCorruptFile() {
        check("corrupt JSON file → empty memory, no crash", () -> {
            writeFile(memFile(), "CORRUPTED {{{");
            EpisodicMemory mem = reloadMem();
            assertThat(mem.totalCount() == 0, "should be empty");
        });
    }

    static void testEpisodicMemoryMultipleWorlds() {
        check("entries stay scoped to their world", () -> {
            EpisodicMemory mem = freshMem();
            mem.addEntry("WorldA", EpisodicMemory.TYPE_NOTE, "A entry");
            mem.addEntry("WorldB", EpisodicMemory.TYPE_NOTE, "B entry");
            mem.addEntry("WorldA", EpisodicMemory.TYPE_NOTE, "A entry 2");
            assertThat(mem.countForWorld("WorldA") == 2, "WorldA has 2");
            assertThat(mem.countForWorld("WorldB") == 1, "WorldB has 1");
            assertThat(mem.getForWorld("WorldB").get(0).summary.equals("B entry"), "WorldB content correct");
        });
    }

    static void testEpisodicMemorySpecialCharsInWorldName() {
        check("special characters in world name stored and retrieved", () -> {
            EpisodicMemory mem = freshMem();
            String world = "World \"Quotes\" & <Tags> / Slashes";
            mem.addEntry(world, EpisodicMemory.TYPE_NOTE, "Test entry");
            assertThat(mem.countForWorld(world) == 1, "stored and retrieved");
            String block = mem.buildPromptBlock(world);
            assertThat(!block.isEmpty(), "prompt block generated");
        });
    }

    static void testEpisodicMemoryVeryLongSummary() {
        check("50,000-char summary stored without truncation", () -> {
            EpisodicMemory mem = freshMem();
            String longSummary = "x".repeat(50_000);
            mem.addEntry("W", EpisodicMemory.TYPE_NOTE, longSummary);
            List<EpisodicMemory.MemoryEntry> entries = mem.getForWorld("W");
            assertThat(entries.get(0).summary.length() == 50_000, "full summary preserved");
        });
    }

    static void testEpisodicMemoryTrimKeepsMostRecent() {
        check("after trim, most recent 60 entries are kept (oldest dropped)", () -> {
            EpisodicMemory mem = freshMem();
            for (int i = 0; i < 80; i++) mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "Entry " + i);
            List<EpisodicMemory.MemoryEntry> entries = mem.getForWorld("W");
            assertThat(entries.size() == 60, "60 remain");
            boolean hasNewest = entries.stream().anyMatch(e -> "Entry 79".equals(e.summary));
            boolean hasOldest = entries.stream().anyMatch(e -> "Entry 0".equals(e.summary));
            assertThat(hasNewest, "Entry 79 (newest) should be present");
            assertThat(!hasOldest, "Entry 0 (oldest) should have been trimmed");
        });
    }

    static void testMemoryPromptBlockOnlyInjectsRecent10() {
        check("buildPromptBlock with 60 entries injects exactly the 10 most recent", () -> {
            EpisodicMemory mem = freshMem();
            for (int i = 0; i < 60; i++) mem.addEntry("W", EpisodicMemory.TYPE_NOTE, "Entry " + i);
            String block = mem.buildPromptBlock("W");
            assertThat(block.contains("Entry 59"), "Entry 59 (most recent) injected");
            assertThat(block.contains("Entry 50"), "Entry 50 injected");
            assertThat(!block.contains("Entry 49"), "Entry 49 NOT injected (outside window)");
        });
    }

    // ══════════════════════════════════════════════════════
    //  CONFIG STORE
    // ══════════════════════════════════════════════════════

    static void testConfigStoreDefaults() {
        check("get with default returns default when key absent", () -> {
            ConfigStore cs = new ConfigStore();
            String val = cs.get("TOTALLY_MISSING_KEY_XYZ_999", "mydefault");
            assertThat("mydefault".equals(val), "got: " + val);
        });
    }

    static void testConfigStoreSetGet() {
        check("set then get in same session", () -> {
            ConfigStore cs = new ConfigStore();
            cs.set("TEST_STRESS_KEY", "stress_value_123");
            assertThat("stress_value_123".equals(cs.get("TEST_STRESS_KEY", "")), "round-trip works");
        });
    }

    static void testConfigStoreMissingKey() {
        check("missing key returns provided default", () -> {
            ConfigStore cs = new ConfigStore();
            assertThat("fallback".equals(cs.get("NO_SUCH_KEY", "fallback")), "fallback returned");
        });
    }

    static void testConfigStorePlaceholderValue() {
        check("'your_key_here' is stored as-is (hasKey check is in LLMClient)", () -> {
            ConfigStore cs = new ConfigStore();
            cs.set("GROQ_API_KEY", "your_key_here");
            assertThat("your_key_here".equals(cs.get("GROQ_API_KEY", "")), "placeholder stored");
        });
    }

    static void testConfigStoreEmptyStringValue() {
        check("empty string set and retrieved", () -> {
            ConfigStore cs = new ConfigStore();
            cs.set("EMPTY_KEY", "");
            String val = cs.get("EMPTY_KEY", "default");
            assertThat("".equals(val) || "default".equals(val), "empty or default: " + val);
        });
    }

    // ══════════════════════════════════════════════════════
    //  CONVERSATION HISTORY
    // ══════════════════════════════════════════════════════

    static void testConversationHistoryAddRetrieve() {
        check("add user + assistant, retrieve in order", () -> {
            ConversationHistory h = new ConversationHistory(false);
            h.addUser("hello");
            h.addAssistant("hi there");
            List<ConversationHistory.Message> msgs = h.getHistory();
            assertThat(msgs.size() == 2, "2 messages");
            assertThat("user".equals(msgs.get(0).role), "first is user");
            assertThat("hello".equals(msgs.get(0).content), "content correct");
            assertThat("assistant".equals(msgs.get(1).role), "second is assistant");
        });
    }

    static void testConversationHistoryRoles() {
        check("roles are exactly 'user' and 'assistant'", () -> {
            ConversationHistory h = new ConversationHistory(false);
            h.addUser("q");
            h.addAssistant("a");
            List<ConversationHistory.Message> msgs = h.getHistory();
            assertThat("user".equals(msgs.get(0).role), "role=user");
            assertThat("assistant".equals(msgs.get(1).role), "role=assistant");
        });
    }

    static void testConversationHistoryClear() {
        check("clear empties history", () -> {
            ConversationHistory h = new ConversationHistory(false);
            h.addUser("a"); h.addAssistant("b");
            h.clear();
            assertThat(h.getHistory().isEmpty(), "should be empty after clear");
        });
    }

    static void testConversationHistoryEmpty() {
        check("empty history returns non-null empty list", () -> {
            ConversationHistory h = new ConversationHistory(false);
            List<ConversationHistory.Message> msgs = h.getHistory();
            assertThat(msgs != null, "not null");
            assertThat(msgs.isEmpty(), "empty");
        });
    }

    static void testConversationHistoryVeryLongMessage() {
        check("50,000-char message stored intact", () -> {
            ConversationHistory h = new ConversationHistory(false);
            String longMsg = "x".repeat(50_000);
            h.addUser(longMsg);
            assertThat(h.getHistory().get(0).content.length() == 50_000, "full length preserved");
        });
    }

    static void testConversationHistory10kMessages() {
        check("10,000 alternating messages stored without crash", () -> {
            ConversationHistory h = new ConversationHistory(false);
            for (int i = 0; i < 5_000; i++) {
                h.addUser("user message " + i);
                h.addAssistant("assistant reply " + i);
            }
            assertThat(h.getHistory().size() == 10_000, "10k messages, got " + h.getHistory().size());
        });
    }

    // ══════════════════════════════════════════════════════
    //  MODULE MANAGER
    // ══════════════════════════════════════════════════════

    static void testModuleManagerDefaults() {
        check("correct defaults: ASSISTANT/MATH/MEMORY on, rest off", () -> {
            ModuleManager mm = new ModuleManager();
            assertThat(mm.isEnabled("ASSISTANT_MODE"), "ASSISTANT_MODE on");
            assertThat(mm.isEnabled("MATH_ENGINE"),    "MATH_ENGINE on");
            assertThat(mm.isEnabled("MEMORY"),         "MEMORY on");
            assertThat(!mm.isEnabled("ORACLE_MODE"),   "ORACLE_MODE off");
            assertThat(!mm.isEnabled("FOOL_MODE"),     "FOOL_MODE off");
            assertThat(!mm.isEnabled("SCHOLAR_MODE"),  "SCHOLAR_MODE off");
            assertThat(!mm.isEnabled("NPC_MODE"),      "NPC_MODE off");
        });
    }

    static void testModuleManagerAllOn() {
        check("all on — prompt non-empty, contains all behavior keys", () -> {
            ModuleManager mm = new ModuleManager();
            for (String n : mm.getModuleNames()) mm.setEnabled(n, true);
            String text = mm.toPromptText();
            assertThat(text != null && !text.isBlank(), "non-empty");
            assertThat(text.contains("ORACLE"), "ORACLE present");
            assertThat(text.contains("FOOL"),   "FOOL present");
            assertThat(text.contains("SCHOLAR"),"SCHOLAR present");
        });
    }

    static void testModuleManagerAllOff() {
        check("all off — prompt contains fallback text", () -> {
            ModuleManager mm = new ModuleManager();
            for (String n : mm.getModuleNames()) mm.setEnabled(n, false);
            String text = mm.toPromptText();
            assertThat(text.contains("No specific mode"), "fallback present: " + text.substring(0, Math.min(80, text.length())));
        });
    }

    static void testModuleManagerOraclePromptHasJson() {
        check("ORACLE-only prompt contains all required JSON field names", () -> {
            ModuleManager mm = new ModuleManager();
            for (String n : mm.getModuleNames()) mm.setEnabled(n, false);
            mm.setEnabled("ORACLE_MODE", true);
            String text = mm.toPromptText();
            for (String field : new String[]{"verdict","confidence","reasoning","signals",
                    "counter_scenario","references","domains","subject","timeframe"}) {
                assertThat(text.contains("\"" + field + "\""), "field '" + field + "' in ORACLE prompt");
            }
        });
    }

    static void testModuleManagerSaveLoad() {
        check("setEnabled persists across reload", () -> {
            ModuleManager mm1 = new ModuleManager();
            mm1.setEnabled("ORACLE_MODE", true);
            mm1.setEnabled("FOOL_MODE", true);
            mm1.setEnabled("ASSISTANT_MODE", false);
            ModuleManager mm2 = new ModuleManager();
            assertThat(mm2.isEnabled("ORACLE_MODE"),    "ORACLE persisted");
            assertThat(mm2.isEnabled("FOOL_MODE"),      "FOOL persisted");
            assertThat(!mm2.isEnabled("ASSISTANT_MODE"),"ASSISTANT off persisted");
            // reset
            mm2.setEnabled("ORACLE_MODE", false);
            mm2.setEnabled("FOOL_MODE", false);
            mm2.setEnabled("ASSISTANT_MODE", true);
        });
    }

    static void testModuleManagerGetActiveModeLabel() {
        check("getActiveModeLabel reflects active modes", () -> {
            ModuleManager mm = new ModuleManager();
            for (String n : mm.getModuleNames()) mm.setEnabled(n, false);
            assertThat("Default".equals(mm.getActiveModeLabel()), "Default when none active");
            mm.setEnabled("ORACLE_MODE", true);
            assertThat(mm.getActiveModeLabel().contains("ORACLE"), "ORACLE in label");
            mm.setEnabled("SCHOLAR_MODE", true);
            String label = mm.getActiveModeLabel();
            assertThat(label.contains("ORACLE") && label.contains("SCHOLAR"), "both in label: " + label);
            // reset
            mm.setEnabled("ORACLE_MODE", false);
            mm.setEnabled("SCHOLAR_MODE", false);
            mm.setEnabled("ASSISTANT_MODE", true);
        });
    }

    static void testModuleManagerIsOracleActive() {
        check("isOracleModeActive() matches isEnabled('ORACLE_MODE')", () -> {
            ModuleManager mm = new ModuleManager();
            mm.setEnabled("ORACLE_MODE", false);
            assertThat(!mm.isOracleModeActive(), "false when off");
            mm.setEnabled("ORACLE_MODE", true);
            assertThat(mm.isOracleModeActive(), "true when on");
            mm.setEnabled("ORACLE_MODE", false);
        });
    }

    // ══════════════════════════════════════════════════════
    //  INTEGRATION
    // ══════════════════════════════════════════════════════

    static void testIntegrationPredictionToMemory() {
        check("track prediction → memory gets PREDICTION entry", () -> {
            OracleLog log = freshLog();
            EpisodicMemory mem = freshMem();
            String id = log.addEntry("Rune Catchers", "meta balance",
                "Fire dominates", 80, "Very High", "2 weeks", list("game-design"));
            mem.addEntry("Rune Catchers", EpisodicMemory.TYPE_PREDICTION,
                "ORACLE predicted: \"meta balance\" — 80% confidence. Verdict: Fire dominates");
            assertThat(log.openCount() == 1, "1 open prediction");
            assertThat(mem.countForWorld("Rune Catchers") == 1, "1 memory entry");
            String block = mem.buildPromptBlock("Rune Catchers");
            assertThat(block.contains("meta balance"), "subject in memory block");
        });
    }

    static void testIntegrationOutcomeClosesLoop() {
        check("mark outcome → resolved + memory has both PREDICTION and OUTCOME", () -> {
            OracleLog log = freshLog();
            EpisodicMemory mem = freshMem();
            String id = log.addEntry("Rune Catchers", "meta balance",
                "Fire dominates", 80, "Very High", "2 weeks", list("game-design"));
            mem.addEntry("Rune Catchers", EpisodicMemory.TYPE_PREDICTION,
                "ORACLE predicted: \"meta balance\"");

            log.resolveEntry(id, OracleLog.OUTCOME_CORRECT, "Fire did win.");
            mem.addEntry("Rune Catchers", EpisodicMemory.TYPE_OUTCOME,
                "ORACLE prediction \"meta balance\" was correct. Note: Fire did win.");

            assertThat(log.resolvedCount() == 1, "1 resolved");
            assertThat(log.openCount() == 0, "0 open");
            assertThat(mem.countForWorld("Rune Catchers") == 2, "2 memory entries");
            int[] acc = log.getOverallAccuracy();
            assertThat(acc[0] == 1 && acc[1] == 1, "accuracy 1/1");
            String block = mem.buildPromptBlock("Rune Catchers");
            assertThat(block.contains("[PREDICTION]"), "has PREDICTION");
            assertThat(block.contains("[OUTCOME]"), "has OUTCOME");
            assertThat(block.contains("was correct"), "outcome text in block");
        });
    }

    static void testIntegration100PredictionsAccuracy() {
        check("100 predictions: 70 correct, 15 partial, 15 incorrect → 85% accuracy", () -> {
            OracleLog log = freshLog();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < 100; i++) ids.add(
                log.addEntry("W", "subject " + i, "verdict", 70, "High", "1w", list("tech")));
            for (int i = 0;  i < 70; i++) log.resolveEntry(ids.get(i), OracleLog.OUTCOME_CORRECT, "");
            for (int i = 70; i < 85; i++) log.resolveEntry(ids.get(i), OracleLog.OUTCOME_PARTIAL, "");
            for (int i = 85; i < 100; i++) log.resolveEntry(ids.get(i), OracleLog.OUTCOME_INCORRECT, "");
            int[] acc = log.getOverallAccuracy();
            assertThat(acc[1] == 100, "total=100");
            assertThat(acc[0] == 85, "correct+partial=85, got " + acc[0]);
            int pct = (int) Math.round((acc[0] * 100.0) / acc[1]);
            assertThat(pct == 85, "85% accuracy, got " + pct + "%");
            assertThat(pct >= 75, "exceeds 75% target");
        });
    }

    // ══════════════════════════════════════════════════════
    //  LLMCLIENT.searchWeb (network-optional)
    // ══════════════════════════════════════════════════════

    static void testSearchWebSpecialChars() {
        check("special chars in query — no exception (result may be empty if offline)", () -> {
            LLMClient client = new LLMClient(new ConfigStore());
            String result = client.searchWeb("test & query <special> \"quoted\"");
            assertThat(result != null, "result not null");
        });
    }

    static void testSearchWebEmptyQuery() {
        check("empty query — no exception", () -> {
            LLMClient client = new LLMClient(new ConfigStore());
            String result = client.searchWeb("");
            assertThat(result != null, "result not null");
        });
    }

    static void testSearchWebLongQuery() {
        check("1000-char query — no exception", () -> {
            LLMClient client = new LLMClient(new ConfigStore());
            String result = client.searchWeb("what will happen ".repeat(60));
            assertThat(result != null, "result not null");
        });
    }

    static void testSearchWebUnicodeQuery() {
        check("unicode query — no exception", () -> {
            LLMClient client = new LLMClient(new ConfigStore());
            String result = client.searchWeb("probability of 火水 conflict breaking rune meta ≥75%");
            assertThat(result != null, "result not null");
        });
    }

    // ══════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════

    static void resetModuleDefaults() {
        ModuleManager mm = new ModuleManager();
        mm.setEnabled("ASSISTANT_MODE", true);
        mm.setEnabled("MATH_ENGINE",    true);
        mm.setEnabled("MEMORY",         true);
        mm.setEnabled("ORACLE_MODE",    false);
        mm.setEnabled("FOOL_MODE",      false);
        mm.setEnabled("SCHOLAR_MODE",   false);
        mm.setEnabled("NPC_MODE",       false);
    }

    static OracleLog freshLog() {
        logFile().delete();
        return new OracleLog() {
            @Override protected File resolveFile() { return logFile(); }
        };
    }

    static OracleLog reloadLog() {
        return new OracleLog() {
            @Override protected File resolveFile() { return logFile(); }
        };
    }

    static EpisodicMemory freshMem() {
        memFile().delete();
        return new EpisodicMemory() {
            @Override protected File resolveFile() { return memFile(); }
        };
    }

    static EpisodicMemory reloadMem() {
        return new EpisodicMemory() {
            @Override protected File resolveFile() { return memFile(); }
        };
    }

    static File logFile() { return new File(TEST_DIR, "oracle_log.json"); }
    static File memFile() { return new File(TEST_DIR, "episodic_memory.json"); }

    static void writeFile(File f, String content) {
        try { Files.writeString(f.toPath(), content); } catch (IOException e) { throw new RuntimeException(e); }
    }

    static List<String> list(String... items) { return new ArrayList<>(Arrays.asList(items)); }

    static void assertThat(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void setupTestDir() throws IOException {
        TEST_DIR = Files.createTempDirectory("aria-stress-").toFile();
        System.out.println("Test dir: " + TEST_DIR.getAbsolutePath() + "\n");
    }

    static void cleanupTestDir() {
        deleteDir(TEST_DIR);
    }

    // ═══════════════════════════════════════════════════════
    // v1.3 — NPC MODE, ACCESS CODE, MARKDOWN RENDERER
    // ═══════════════════════════════════════════════════════

    static void testNpcPromptNoAriaIdentity() throws Exception {
        AriaCore core = buildCoreWithWorld("TestWorld", "A merchant in a fantasy bazaar", "Kael the trader");
        core.getModuleManager().setEnabled("NPC_MODE", true);
        String prompt = core.buildSystemPrompt();
        // Must NOT contain ARIA identity phrases
        if (prompt.contains("YOU ARE ARIA") || prompt.contains("Adaptive Reasoning and Intelligence"))
            throw new AssertionError("NPC prompt still contains ARIA identity: " + prompt.substring(0, Math.min(200, prompt.length())));
        // Must contain character name
        if (!prompt.toUpperCase().contains("KAEL"))
            throw new AssertionError("NPC prompt does not contain character name KAEL");
    }

    static void testNpcPromptContainsWorldDesc() throws Exception {
        AriaCore core = buildCoreWithWorld("Rune Catchers", "An open-world rune-hunting RPG where artifacts grant power", "Ryn the Guide");
        core.getModuleManager().setEnabled("NPC_MODE", true);
        String prompt = core.buildSystemPrompt();
        if (!prompt.contains("Rune Catchers"))
            throw new AssertionError("NPC prompt missing world name");
        if (!prompt.contains("rune"))
            throw new AssertionError("NPC prompt missing world description content");
    }

    static void testNpcPromptForbidsAIAwareness() throws Exception {
        AriaCore core = buildCoreWithWorld("Murim", "Ancient martial arts world", "Elder Jang");
        core.getModuleManager().setEnabled("NPC_MODE", true);
        String prompt = core.buildSystemPrompt();
        // Must have absolute rules forbidding AI acknowledgment
        if (!prompt.contains("NOT AN AI") && !prompt.contains("ABSOLUTE RULES"))
            throw new AssertionError("NPC prompt missing absolute identity rules");
    }

    static void testNpcModeOffUsesAriaPrompt() throws Exception {
        AriaCore core = buildCoreWithWorld("TestWorld", "A sci-fi colony", "Commander Shen");
        core.getModuleManager().setEnabled("NPC_MODE", false);
        String prompt = core.buildSystemPrompt();
        // Without NPC mode, should use normal ARIA prompt
        if (prompt.toUpperCase().contains("ABSOLUTE RULES") && prompt.contains("NOT AN AI"))
            throw new AssertionError("Normal mode prompt has NPC identity rules active");
    }

    static void testNpcModeNoWorldFallsBackToNormal() throws Exception {
        AriaCore core = new AriaCore();
        core.getModuleManager().setEnabled("NPC_MODE", true);
        core.getContextManager().deactivate(); // ensure no world context is active
        String prompt = core.buildSystemPrompt();
        // Normal prompt path is used (no world = nothing to embody).
        // Base prompt establishes ARIA identity, NPC module text is appended.
        // The full character-identity replacement (buildNpcPrompt) must NOT be used.
        boolean hasBaseAriaIdentity = prompt.contains("YOU ARE ARIA") ||
            prompt.contains("Adaptive Reasoning") || prompt.contains("raised by") ||
            prompt.contains("raised, not configured") || prompt.contains("Natt") ||
            prompt.contains("WHO YOU ARE");
        if (!hasBaseAriaIdentity)
            throw new AssertionError("Expected ARIA base identity in normal path when no world context is set. Got: "
                + prompt.substring(0, Math.min(300, prompt.length())));
    }

    static void testAccessCodeDetection() throws Exception {
        AriaCore core = new AriaCore();
        if (core.isMemoryUnlocked()) throw new AssertionError("Memory should be locked at start");
        boolean unlocked = core.checkAccessCode("c4wzf90gi4fx");
        if (!unlocked) throw new AssertionError("Access code not recognised");
        if (!core.isMemoryUnlocked()) throw new AssertionError("memoryUnlocked flag not set after correct code");
    }

    static void testAccessCodeWrongInput() throws Exception {
        AriaCore core = new AriaCore();
        boolean unlocked = core.checkAccessCode("wrong-code");
        if (unlocked) throw new AssertionError("Wrong code should not unlock memory");
        if (core.isMemoryUnlocked()) throw new AssertionError("Memory should still be locked");
    }

    static void testAccessCodeEmptyInput() throws Exception {
        AriaCore core = new AriaCore();
        boolean unlocked = core.checkAccessCode("");
        if (unlocked) throw new AssertionError("Empty string should not unlock memory");
    }

    static void testAccessCodeNullInput() throws Exception {
        AriaCore core = new AriaCore();
        boolean unlocked = core.checkAccessCode(null);
        if (unlocked) throw new AssertionError("Null input should not unlock memory");
    }

    static void testLockedMemoryNotInNormalPrompt() throws Exception {
        AriaCore core = new AriaCore();
        core.getModuleManager().setEnabled("NPC_MODE", false);
        core.getContextManager().deactivate();
        String prompt = core.buildSystemPrompt();
        if (prompt.contains("PRIVATE CREATOR MEMORY") || prompt.contains("Nathan Miguel"))
            throw new AssertionError("Locked memory must not appear in normal system prompt");
    }

    static void testLockedMemoryAppearsWhenUnlocked() throws Exception {
        AriaCore core = new AriaCore();
        // Ensure we take the normal (non-NPC) path so locked memory is appended
        core.getModuleManager().setEnabled("NPC_MODE", false);
        core.getContextManager().deactivate();
        core.checkAccessCode("c4wzf90gi4fx");
        String prompt = core.buildSystemPrompt();
        if (!prompt.contains("Nathan Miguel") && !prompt.contains("PRIVATE CREATOR MEMORY"))
            throw new AssertionError("Locked memory missing from prompt after unlock. Got: "
                + prompt.substring(0, Math.min(400, prompt.length())));
    }

    static void testMarkdownDetectionBold() {
        if (!aria.core.MarkdownRenderer.hasMarkdown("This is **bold** text"))
            throw new AssertionError("Should detect **bold**");
    }

    static void testMarkdownDetectionCodeBlock() {
        if (!aria.core.MarkdownRenderer.hasMarkdown("Use ```java\nint x = 1;\n```"))
            throw new AssertionError("Should detect code block");
    }

    static void testMarkdownDetectionHeading() {
        if (!aria.core.MarkdownRenderer.hasMarkdown("# Title\n\nSome text"))
            throw new AssertionError("Should detect # heading");
    }

    static void testMarkdownDetectionUnderscoreBold() {
        if (!aria.core.MarkdownRenderer.hasMarkdown("This is __underline bold__ text"))
            throw new AssertionError("Should detect __bold__");
    }

    static void testMarkdownDetectionPlainText() {
        if (aria.core.MarkdownRenderer.hasMarkdown("Just a normal sentence with no markdown in it."))
            throw new AssertionError("Plain text should NOT be detected as markdown");
    }

    static void testMarkdownDetectionNull() {
        if (aria.core.MarkdownRenderer.hasMarkdown(null))
            throw new AssertionError("Null should return false, not throw");
    }

    static void testMarkdownRenderReturnVBox() {
        // JavaFX node creation needs a running toolkit — skip gracefully if headless
        try {
            javafx.scene.layout.VBox result = aria.core.MarkdownRenderer.render("Hello **world**", 400, true);
            if (result == null) throw new AssertionError("render() returned null");
            if (result.getChildren().isEmpty()) throw new AssertionError("render() returned empty VBox");
        } catch (RuntimeException | Error e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("toolkit") || msg.toLowerCase().contains("graphics")
                    || msg.toLowerCase().contains("platform")) {
                System.out.print("  [headless, skipped] ");
                return; // pass — FX not available in this env, not a code bug
            }
            throw new AssertionError("render() threw unexpected: " + e);
        }
    }

    static void testMarkdownRenderCodeBlock() {
        try {
            String md = "Here is code:\n```java\nint x = 1;\n```\nDone.";
            javafx.scene.layout.VBox result = aria.core.MarkdownRenderer.render(md, 400, true);
            if (result == null) throw new AssertionError("render() with code block returned null");
            if (result.getChildren().size() < 2) throw new AssertionError("Should have multiple nodes for code block");
        } catch (RuntimeException | Error e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("toolkit") || msg.toLowerCase().contains("graphics")
                    || msg.toLowerCase().contains("platform")) {
                System.out.print("  [headless, skipped] ");
                return;
            }
            throw new AssertionError("render() threw unexpected: " + e);
        }
    }

    static void testMarkdownRenderEmpty() {
        try {
            javafx.scene.layout.VBox result = aria.core.MarkdownRenderer.render("", 400, false);
            if (result == null) throw new AssertionError("render() of empty string returned null");
        } catch (RuntimeException | Error e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("toolkit") || msg.toLowerCase().contains("graphics")
                    || msg.toLowerCase().contains("platform")) {
                System.out.print("  [headless, skipped] ");
                return;
            }
            throw new AssertionError("render() threw unexpected: " + e);
        }
    }

    static void testNpcPromptBuildPerformance() throws Exception {
        AriaCore core = buildCoreWithWorld("Perf World", "A big world", "Speed Character");
        core.getModuleManager().setEnabled("NPC_MODE", true);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 200; i++) core.buildSystemPrompt();
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 3000)
            throw new AssertionError("buildNpcPrompt() too slow: " + elapsed + "ms for 200 calls");
    }

    // Helper — create AriaCore with a world context already set
    static AriaCore buildCoreWithWorld(String worldName, String worldDesc, String ariaRole) {
        AriaCore core = new AriaCore();
        com.google.gson.JsonObject ctx = new com.google.gson.JsonObject();
        ctx.addProperty("world_name",        worldName);
        ctx.addProperty("world_description", worldDesc);
        ctx.addProperty("aria_role",         ariaRole);
        ctx.addProperty("knowledge_bounds",  "Everything native to " + worldName);
        ctx.addProperty("knowledge_limits",  "Technology beyond this world");
        core.getContextManager().addOrUpdate(worldName, ctx);
        core.getContextManager().setActive(worldName);
        return core;
    }

    static void deleteDir(File dir) {
        if (dir == null) return;
        if (dir.isDirectory()) { File[] kids = dir.listFiles(); if (kids != null) for (File f : kids) deleteDir(f); }
        dir.delete();
    }

    static void runSection(String name, RunnableEx block) {
        System.out.println("── " + name + " " + "─".repeat(Math.max(2, 54 - name.length())));
        try { block.run(); } catch (Exception e) { fail("SECTION CRASHED: " + name, e.toString()); }
        System.out.println();
    }

    static void check(String name, RunnableEx test) {
        try {
            test.run();
            System.out.printf("  ✓  %s%n", name);
            passed++;
        } catch (AssertionError e) {
            String msg = e.getMessage() != null ? e.getMessage() : "assertion failed";
            System.out.printf("  ✗  %s%n       → %s%n", name, msg);
            failures.add("[FAIL] " + name + " → " + msg);
            failed++;
        } catch (Exception e) {
            System.out.printf("  ✗  %s%n       → EXCEPTION: %s%n", name, e);
            failures.add("[EXCP] " + name + " → " + e);
            failed++;
        }
    }

    static void fail(String name, String reason) {
        System.out.printf("  ✗  %s: %s%n", name, reason);
        failures.add("[FAIL] " + name + ": " + reason);
        failed++;
    }

    static void printSummary() {
        System.out.println("══════════════════════════════════════════════════════");
        System.out.printf("  RESULTS:  %d passed   %d failed   %d total%n", passed, failed, passed + failed);
        if (!failures.isEmpty()) {
            System.out.println("\n  FAILURES:");
            failures.forEach(f -> System.out.println("    " + f));
        }
        System.out.println("══════════════════════════════════════════════════════\n");
    }

    @FunctionalInterface interface RunnableEx { void run() throws Exception; }
}
