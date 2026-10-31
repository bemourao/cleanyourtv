package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The owner's app groups (folders on Home), kept in the launcher's private files (groups.txt).
 *
 * Format, UTF-8 text, one group per line: the name, a tab, then the member packages separated by commas
 * (empty for a group with no apps yet). An app belongs to at most one group. A group is one tile on Home;
 * its apps leave the grid and open from the group's list.
 */
final class Groups {

    private static final String TAG = "TvHome.Groups";
    static final String FILE_NAME = "groups.txt";
    static final int MAX_NAME = 24;

    private Groups() {}

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** A name that is safe to store and show: no tabs, line breaks or bidi controls, trimmed, at most MAX_NAME. */
    static String cleanName(CharSequence raw) {
        if (raw == null) return "";
        String s = raw.toString()
                .replaceAll("[\\t\\r\\n\\u2028\\u2029\\u202A-\\u202E\\u2066-\\u2069]", " ")
                .replaceAll(" +", " ").trim();
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME).trim();
        return s;
    }

    private static boolean validPackage(String pkg) {
        return pkg != null && pkg.matches("[A-Za-z0-9_.]+");
    }

    static LinkedHashMap<String, List<String>> parse(String text) {
        LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
        if (text == null) return groups;
        for (String line : text.split("\n", -1)) {
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            String name = cleanName(line.substring(0, tab));
            if (name.isEmpty() || groups.containsKey(name)) continue;
            List<String> members = new ArrayList<>();
            for (String pkg : line.substring(tab + 1).trim().split(",")) {
                if (validPackage(pkg) && !members.contains(pkg)) members.add(pkg);
            }
            groups.put(name, members);
        }
        return groups;
    }

    static String encode(Map<String, List<String>> groups) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            sb.append(e.getKey()).append('\t').append(String.join(",", e.getValue())).append('\n');
        }
        return sb.toString();
    }

    static LinkedHashMap<String, List<String>> load(Context ctx) {
        File f = file(ctx);
        if (!f.isFile()) return new LinkedHashMap<>();
        try {
            return parse(new String(new AtomicFile(f).readFully(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "groups unreadable, ignoring", e);
            return new LinkedHashMap<>();
        }
    }

    private static boolean save(Context ctx, Map<String, List<String>> groups) {
        AtomicFile atomic = new AtomicFile(file(ctx));
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(encode(groups).getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(out);
            return true;
        } catch (IOException e) {
            if (out != null) atomic.failWrite(out);
            Log.w(TAG, "could not save groups", e);
            return false;
        }
    }

    /** Package to the name of the group that holds it. */
    static Map<String, String> owners(Map<String, List<String>> groups) {
        Map<String, String> owners = new HashMap<>();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            for (String pkg : e.getValue()) owners.put(pkg, e.getKey());
        }
        return owners;
    }

    /** Creates the group if it is new and puts the app in it, taking it out of any other group. */
    static boolean addApp(Context ctx, String group, String pkg) {
        String name = cleanName(group);
        if (name.isEmpty() || !validPackage(pkg)) return false;
        LinkedHashMap<String, List<String>> groups = load(ctx);
        for (List<String> members : groups.values()) members.remove(pkg);
        List<String> members = groups.get(name);
        if (members == null) {
            members = new ArrayList<>();
            groups.put(name, members);
        }
        members.add(pkg);
        return save(ctx, groups);
    }

    static boolean removeApp(Context ctx, String group, String pkg) {
        LinkedHashMap<String, List<String>> groups = load(ctx);
        List<String> members = groups.get(group);
        if (members == null || !members.remove(pkg)) return false;
        return save(ctx, groups);
    }

    /** Deletes the group; its apps go back to the grid. */
    static boolean delete(Context ctx, String group) {
        LinkedHashMap<String, List<String>> groups = load(ctx);
        if (groups.remove(group) == null) return false;
        return save(ctx, groups);
    }
}
