package com.simplelogicanalyzer;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.chart.XYChart;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.ArrayList;

public class FileChangeListener implements Runnable{
    private final JsonData configData;
    private final SimpleBooleanProperty collectingData;
    private final ArrayList<Signal> signals;

    public FileChangeListener(JsonData configData, SimpleBooleanProperty collectingData, ArrayList<Signal> signals) {
        this.configData = configData;
        this.collectingData = collectingData;
        this.signals = signals;
    }

    @Override
    public void run() {
        Path filePath = Paths.get(configData.getLogicProbe());
        try {
            WatchService service = FileSystems.getDefault().newWatchService();
            filePath.getParent().register(service, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
            while(true){
                WatchKey key = service.take();
                for(WatchEvent<?> event : key.pollEvents()){
                    if(filePath.getFileName().equals(event.context())){
                        // File has changed, get the last line
                        RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
                        long length = raf.length() - 1;
                        StringBuilder sb = new StringBuilder();

                        for (long pointer = length; pointer >= 0; pointer--) {
                            raf.seek(pointer);
                            char c = (char) raf.read();
                            if (c == '\n' && pointer < length) {
                                break;
                            }
                            sb.append(c);
                        }

                        String newLine = sb.reverse().toString().strip();
                        String[] messageSplit = newLine.split(" ");

                        Platform.runLater(() -> {
                            for(int i = 0; i < messageSplit.length; i++){
                                if(i > configData.getSignals().size()) break;

                                if(collectingData.get()){
                                    Signal signal = signals.get(i);
                                    try{
                                        signal.series.getData().add(new XYChart.Data<>(
                                                signal.series.getData().size(),
                                                Integer.parseInt(messageSplit[i])
                                        ));
                                    } catch (NumberFormatException e){
                                        break;
                                    }
                                }
                            }
                        });
                    }
                }
                key.reset();
                Thread.sleep(100);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
