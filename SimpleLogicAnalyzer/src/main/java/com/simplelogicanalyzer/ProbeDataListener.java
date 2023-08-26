package com.simplelogicanalyzer;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;

public class ProbeDataListener implements ListChangeListener<DataPoint> {
    private final JsonData configData;
    private final SimpleBooleanProperty collectingData;
    private final ArrayList<Signal> signals;
    public ProbeDataListener(JsonData configData, SimpleBooleanProperty collectingData, ArrayList<Signal> signals) {
        this.configData = configData;
        this.collectingData = collectingData;
        this.signals = signals;
    }

    @Override
    public void onChanged(Change<? extends DataPoint> change) {
        change.next();

        if(change.getAddedSubList().isEmpty()) return;

        String newData = change.getAddedSubList().get(0).content;
        String[] dataSplit = newData.split(" ");
        Platform.runLater(() -> {
            for(int i = 0; i < dataSplit.length; i++){
                if(i > configData.getSignals().size()) break;

                if(collectingData.get()){
                    Signal signal = signals.get(i);
                    try{
                        signal.series.getData().add(new XYChart.Data<>(
                                signal.series.getData().size(),
                                Integer.parseInt(dataSplit[i])
                        ));
                    } catch (NumberFormatException e){
                        break;
                    }
                }
            }
        });
    }
}
