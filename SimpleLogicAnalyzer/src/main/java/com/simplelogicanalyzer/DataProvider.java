package com.simplelogicanalyzer;

import javafx.collections.ObservableList;
import javafx.scene.control.Button;

import java.util.ArrayList;
import java.util.List;

public interface DataProvider {
    ArrayList<ObservableList<DataPoint>> getLogDataList();
    void send(String msg);
    void clear();
}
