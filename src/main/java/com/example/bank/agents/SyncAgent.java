package com.example.bank.agents;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyncAgent implements Serializable {
    public Map<String, Boolean> fileList = new ConcurrentHashMap<>();
    private final String fileDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";


    public SyncAgent() {
        File folder = new File(fileDirectory);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                // Only add files (ignore directories)
                if (file.isFile()) {
                    String fileName = file.getName();
                    fileList.putIfAbsent(fileName, false);
                }
            }
        }
    }
    public boolean checkLockFile(String fileName) {
        return fileList.get(fileName);
    }

    public void lockFile(String fileName) {
        fileList.put(fileName, true);
    }

    public void unlockFile(String fileName) {
        fileList.put(fileName, false);
    }
}
