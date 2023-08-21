#define ADC_THRESHOLD 512
#define PIN_COUNT 2
int pins[PIN_COUNT] = {A0, A1};

String binaryValue(int adcReading){
  if(adcReading > ADC_THRESHOLD){
    return "1";
  }
  return "0";
}

void setup() {
  Serial.begin(9600);
}

void loop() {
  for(int i = 0; i < PIN_COUNT; i++){
    Serial.print(binaryValue(analogRead(pins[i])) + " ");
  }
  Serial.println("");
}
