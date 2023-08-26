package com.simplelogicanalyzer;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;

public class SerialPortDataListenerImpl implements SerialPortDataListener {
    private String buffer = "";
    public String receivedMessage = "";
    public ObservableList<DataPoint> rawDataList;
    public SimpleBooleanProperty collectingData;
    public SerialPortDataListenerImpl(ObservableList<DataPoint> rawDataList, SimpleBooleanProperty collectingData) {
        this.rawDataList = rawDataList;
        this.collectingData = collectingData;
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    @Override
    public void serialEvent(SerialPortEvent serialPortEvent) {
        SerialPort serialPort = serialPortEvent.getSerialPort();

        if(receivedMessage.equals("")){
            int bytesAvailable = serialPort.bytesAvailable();
            byte[] byteBuffer = new byte[bytesAvailable];
            serialPort.readBytes(byteBuffer, bytesAvailable);

            receivedMessage = new String(byteBuffer);
        }

        if(receivedMessage.indexOf('\r') != -1){
            receivedMessage = buffer.concat(receivedMessage);
            buffer = "";

            long fullMessageCount = receivedMessage.chars().filter(c -> c == '\r').count();
            String[] messageSplit = receivedMessage.split("\r");
            for(int i = 0; i < messageSplit.length; i++){
                if(i < fullMessageCount){
                    int finalI = i;
                    Platform.runLater(() -> {
                        if(collectingData.get()){
                            String content = messageSplit[finalI].trim();
                            rawDataList.add(0, new DataPoint(content, System.currentTimeMillis()));
                        }
                    });
                } else {
                    buffer = buffer.concat(messageSplit[i]);
                }
            }
        } else {
            buffer = buffer.concat(receivedMessage);
        }

        receivedMessage = "";
    }
}
