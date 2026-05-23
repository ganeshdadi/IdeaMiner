package com.ideaminer.web;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

@Service
public class LocalFolderPickerService {
    public Map<String, String> chooseFolder() {
        try {
            Process process = new ProcessBuilder(
                    "osascript",
                    "-e",
                    "POSIX path of (choose folder with prompt \"Select repository folder\")"
            ).start();
            int exit = process.waitFor();
            if (exit != 0) {
                String err = read(process.getErrorStream());
                return Map.of("error", err == null || err.isBlank() ? "Folder selection canceled." : err.trim());
            }
            String output = read(process.getInputStream()).trim();
            if (output.isBlank()) {
                return Map.of("error", "No folder selected.");
            }
            return Map.of("path", output);
        } catch (Exception exception) {
            return Map.of("error", "Folder picker failed: " + exception.getMessage());
        }
    }

    private String read(java.io.InputStream stream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        }
    }
}
