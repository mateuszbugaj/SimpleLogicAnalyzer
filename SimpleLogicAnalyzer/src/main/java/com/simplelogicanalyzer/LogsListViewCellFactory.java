package com.simplelogicanalyzer;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LogsListViewCellFactory implements Callback<ListView<DataPoint>, ListCell<DataPoint>> {
    private final SimpleLongProperty timestamp;

    public LogsListViewCellFactory(SimpleLongProperty timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public ListCell<DataPoint> call(ListView<DataPoint> param) {
        return new LogListCell(timestamp);
    }
}

class LogListCell extends ListCell<DataPoint> {
    private final SimpleLongProperty timestamp;

    public LogListCell(SimpleLongProperty timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    protected void updateItem(DataPoint dataPoint, boolean empty) {
        super.updateItem(dataPoint, empty);
        Platform.runLater(() -> {
            if (dataPoint != null && !empty) {
                SimpleDateFormat sdf = new SimpleDateFormat("[ss.SSS] ");
                long timeDelta = dataPoint.timestamp - timestamp.get();
                String formattedDate = sdf.format(new Date(timeDelta));
                setText(formattedDate + dataPoint.content);
            } else {
                setText(null);
            }
        });
    }
}