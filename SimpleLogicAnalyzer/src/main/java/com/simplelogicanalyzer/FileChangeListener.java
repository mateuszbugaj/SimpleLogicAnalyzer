package com.simplelogicanalyzer;

import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;

public class FileChangeListener implements Runnable{
    private final Path filepath;
    private final ObservableList<String> output;
    private long lastKnownPosition = 0;

    public FileChangeListener(String filePath, ObservableList<String> output) {
        this.filepath = Paths.get(filePath);
        this.output = output;
    }

    @Override
    public void run() {
        try {
            WatchService service = FileSystems.getDefault().newWatchService();
            filepath.getParent().register(service, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) { // todo: check how to interrupt this loop
                WatchKey key = service.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (filepath.getFileName().equals(event.context())) {

                        // File has changed, get the new lines
                        try (RandomAccessFile raf = new RandomAccessFile(filepath.toFile(), "r")) {
                            long currentLength = raf.length();
                            if(currentLength < lastKnownPosition){
                                lastKnownPosition = 0;
                            } else if (currentLength > lastKnownPosition) {
                                raf.seek(lastKnownPosition); // Move to the last known position

                                String line;
                                while ((line = raf.readLine()) != null) {
                                    if(!line.isBlank()){
                                        output.add(line.strip());
                                    }
                                }

                                lastKnownPosition = currentLength; // Update the last known position
                            }
                        }
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
