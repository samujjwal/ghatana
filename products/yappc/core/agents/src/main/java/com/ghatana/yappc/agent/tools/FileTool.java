package com.ghatana.yappc.agent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/** File operations tool. 
 * @doc.type class
 * @doc.purpose Handles file tool operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class FileTool {
    
    public static String read(String path) {
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    public static String write(String path, String content) {
        try {
            Files.writeString(Paths.get(path), content);
            return "SUCCESS: File written to " + path;
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    public static String exists(String path) {
        return String.valueOf(Files.exists(Paths.get(path)));
    }
    
    public static String list(String dirPath) {
        try {
            return Files.list(Paths.get(dirPath))
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
