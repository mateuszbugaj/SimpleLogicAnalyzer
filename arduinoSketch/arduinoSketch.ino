#define ADC_THRESHOLD 512
#define PIN_COUNT 6
#define INTERVAL 30
#define ACTIVATION_LED 13
int pins[PIN_COUNT] = {A0, A1, A2, A3, A4, A5};
bool start = false;

String binaryValue(int adcReading){
  if(adcReading > ADC_THRESHOLD){
    return "1";
  }
  return "0";
}

void setup() {
  Serial.begin(9600);
  pinMode(ACTIVATION_LED, OUTPUT);
  digitalWrite(ACTIVATION_LED, LOW);
  for(int i = 0; i < PIN_COUNT; i++){
    pinMode(pins[i], INPUT);
  }
}

void loop() {
  if(Serial.available() > 0){
    String payload = Serial.readString();
    payload.trim();
    if(payload == "start"){
      start = true;
      digitalWrite(ACTIVATION_LED, HIGH);
    }

    if(payload == "stop"){
      start = false;
      digitalWrite(ACTIVATION_LED, LOW);
    }
  }

  if(start == true){
    for(int i = 0; i < PIN_COUNT; i++){
      Serial.print(binaryValue(analogRead(pins[i])) + " ");
    }
    Serial.println("");
    delay(INTERVAL);
  }

}
