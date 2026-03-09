package com.ghatana.refactorer.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads AllowlistPolicy from a JSON file at config/debug/safe-commands.allowlist.json 
 * @doc.type class
 * @doc.purpose Handles allowlist policy loader operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class AllowlistPolicyLoader {
    private static final Pattern ITEM =
            Pattern.compile(
                    "\\{\\s*\"name\"\\s*:\\s*\"([^\"]*)\"[\\s,]*\"cmd\"\\s*:\\s*\\[(.*?)\\][\\s,]*\"allowArgs\"\\s*:\\s*\\[(.*?)\\]",
                    Pattern.DOTALL);
    private static final Pattern ARRAY_ELEM = Pattern.compile("\"([^\"]*)\"");

    private AllowlistPolicyLoader() {}

    public static AllowlistPolicy load(Path file) throws IOException {
        String json = Files.readString(file);
        List<AllowlistPolicy.AllowedCommand> commands = new ArrayList<>();
        Matcher m = ITEM.matcher(json);
        while (m.find()) {
            String name = m.group(1);
            String cmdArr = m.group(2);
            String allowArr = m.group(3);
            List<String> cmd = extractArray(cmdArr);
            List<String> allowArgs = extractArray(allowArr);
            commands.add(new AllowlistPolicy.AllowedCommand(name, cmd, allowArgs));
        }
        return new AllowlistPolicy(commands);
    }

    private static List<String> extractArray(String arr) {
        List<String> out = new ArrayList<>();
        Matcher m = ARRAY_ELEM.matcher(arr);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
