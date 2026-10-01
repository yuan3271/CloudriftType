import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

/**
 * Offline generator for the bundled Chinese dictionaries.
 *
 * Input : jieba dict.txt  (word, frequency, part-of-speech)  -- MIT licensed
 * Tool  : pinyin4j 2.5.1   (hanzi -> hanyu pinyin readings)  -- BSD licensed
 * Output: pinyin_words.txt (pinyin, word, score)
 *         pinyin_chars.txt (syllable, hanzi ordered by corpus weight)
 *
 * Usage: java -cp lib/pinyin4j-2.5.1.jar:. DictGen <jieba dict> <assets dir>
 */
public final class DictGen {

    private static final int MAX_WORD_LENGTH = 4;
    private static final int MIN_WORD_FREQ = 120;
    private static final int MAX_READING_COMBINATIONS = 8;
    private static final int MAX_WORDS_PER_READING = 8;

    private static final HanyuPinyinOutputFormat FORMAT = buildFormat();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: DictGen <jieba dict.txt> <assets dir>");
            System.exit(2);
        }
        String dictPath = args[0];
        String assetsDir = args[1];

        Map<String, Integer> charWeight = new HashMap<>();
        // reading -> word -> score
        Map<String, Map<String, Integer>> wordIndex = new HashMap<>();

        int readWords = 0;
        int keptWords = 0;
        Map<String, List<List<String>>> readingCache = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(dictPath), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int firstSpace = line.indexOf(' ');
                if (firstSpace <= 0) continue;
                int secondSpace = line.indexOf(' ', firstSpace + 1);
                String word = line.substring(0, firstSpace);
                String freqText = secondSpace < 0
                        ? line.substring(firstSpace + 1)
                        : line.substring(firstSpace + 1, secondSpace);
                int freq;
                try {
                    freq = Integer.parseInt(freqText);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                readWords++;
                if (!isAllHan(word)) continue;
                if (word.length() > MAX_WORD_LENGTH) continue;

                List<List<String>> readings = readingsFor(word, readingCache);
                if (readings.isEmpty()) continue;

                // Every character contributes to its own frequency ranking. Long words are
                // split evenly so that a frequent 4-character idiom does not swamp a single
                // character that appears everywhere.
                int perChar = Math.max(1, freq / word.length());
                for (int i = 0; i < word.length(); i++) {
                    String ch = word.substring(i, i + 1);
                    charWeight.merge(ch, perChar, Integer::sum);
                }

                if (word.length() < 2) continue;
                if (freq < MIN_WORD_FREQ) continue;
                keptWords++;

                Set<String> emitted = new HashSet<>();
                for (List<String> combination : readings) {
                    String reading = String.join("", combination);
                    if (!emitted.add(reading)) continue;
                    wordIndex
                            .computeIfAbsent(reading, key -> new LinkedHashMap<>())
                            .merge(word, freq, Math::max);
                }
            }
        }

        writeWords(new java.io.File(assetsDir, "pinyin_words.txt"), wordIndex);
        writeChars(new java.io.File(assetsDir, "pinyin_chars.txt"), charWeight);

        System.out.printf("words read=%d kept=%d unique readings=%d%n",
                readWords, keptWords, wordIndex.size());
    }

    private static void writeWords(java.io.File out, Map<String, Map<String, Integer>> wordIndex)
            throws Exception {
        long written = 0;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8), 1 << 16)) {
            List<String> readings = new ArrayList<>(wordIndex.keySet());
            java.util.Collections.sort(readings);
            for (String reading : readings) {
                Map<String, Integer> words = wordIndex.get(reading);
                List<Map.Entry<String, Integer>> entries = new ArrayList<>(words.entrySet());
                entries.sort(Comparator
                        .comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed()
                        .thenComparing(Map.Entry::getKey));
                int limit = Math.min(entries.size(), MAX_WORDS_PER_READING);
                for (int i = 0; i < limit; i++) {
                    Map.Entry<String, Integer> entry = entries.get(i);
                    writer.write(reading);
                    writer.write('\t');
                    writer.write(entry.getKey());
                    writer.write('\t');
                    writer.write(Integer.toString(entry.getValue()));
                    writer.write('\n');
                    written++;
                }
            }
        }
        System.out.printf("wrote %d word rows to %s (%d bytes)%n", written, out, out.length());
    }

    private static void writeChars(java.io.File out, Map<String, Integer> charWeight)
            throws Exception {
        Map<String, List<String>> bySyllable = new TreeMap<>();
        int skipped = 0;
        for (Map.Entry<String, Integer> entry : charWeight.entrySet()) {
            String ch = entry.getKey();
            List<List<String>> readings = readingsFor(ch, null);
            if (readings.isEmpty()) {
                skipped++;
                continue;
            }
            bySyllable
                    .computeIfAbsent(readings.get(0).get(0), key -> new ArrayList<>())
                    .add(ch);
        }
        Map<String, Integer> weight = charWeight;
        long written = 0;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8), 1 << 16)) {
            for (Map.Entry<String, List<String>> entry : bySyllable.entrySet()) {
                List<String> chars = entry.getValue();
                chars.sort(Comparator
                        .comparingInt((String c) -> -weight.getOrDefault(c, 0))
                        .thenComparing(c -> c));
                StringBuilder builder = new StringBuilder();
                for (String c : chars) builder.append(c);
                writer.write(entry.getKey());
                writer.write('\t');
                // Sorted from most to least frequent, so the runtime can stop as soon as it
                // has enough candidates.
                writer.write(builder.toString());
                writer.write('\n');
                written += chars.size();
            }
        }
        System.out.printf("wrote %d chars to %s (%d bytes), skipped=%d%n",
                written, out, out.length(), skipped);
    }

    /** All reading combinations for [word], capped so that polyphones stay bounded. */
    private static List<List<String>> readingsFor(String word, Map<String, List<List<String>>> cache) {
        if (cache != null && cache.containsKey(word)) {
            return cache.get(word);
        }
        List<List<String>> combos = new ArrayList<>();
        combos.add(new ArrayList<>());
        boolean ok = true;
        for (int i = 0; i < word.length() && ok; i++) {
            String ch = word.substring(i, i + 1);
            String[] syllables = readSyllables(ch);
            if (syllables.length == 0) {
                ok = false;
                break;
            }
            int take = Math.min(syllables.length, 2);
            if (combos.size() * take > MAX_READING_COMBINATIONS) {
                take = 1;
            }
            List<List<String>> expanded = new ArrayList<>();
            for (List<String> prefix : combos) {
                for (int s = 0; s < take; s++) {
                    List<String> next = new ArrayList<>(prefix);
                    next.add(syllables[s]);
                    expanded.add(next);
                }
            }
            combos = expanded;
        }
        if (!ok) combos = List.of();
        if (cache != null) cache.put(word, combos);
        return combos;
    }

    private static String[] readSyllables(String ch) {
        try {
            String[] raw = PinyinHelper.toHanyuPinyinStringArray(ch.charAt(0), FORMAT);
            return raw == null ? new String[0] : raw;
        } catch (Exception e) {
            return new String[0];
        }
    }

    private static boolean isAllHan(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c < 0x4E00 || c > 0x9FFF) return false;
        }
        return word.length() > 0;
    }

    private static HanyuPinyinOutputFormat buildFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }
}
