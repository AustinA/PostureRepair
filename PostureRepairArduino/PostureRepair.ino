#include <Arduino.h>
#include <i2c_t3.h>
#include <Adafruit_BNO055_t3.h>
#include <SoftwareSerial.h>
#include <ArduinoJson.h>

// Defined Constants
#define BNO055_ADDRESS 0x28
#define BNO055_CHIP_ID 0x00
#define rxPin 0
#define txPin 1

// BNO055 object representation
Adafruit_BNO055 bno = Adafruit_BNO055(WIRE_BUS, -1, BNO055_ADDRESS_A, I2C_MASTER, I2C_PINS_18_19, I2C_PULLUP_EXT, I2C_RATE_400, I2C_OP_MODE_ISR);

// Enable UART serial connection from Teensy to Bluetooth Mate
SoftwareSerial btSerial = SoftwareSerial(rxPin, txPin);

bool began = false;

/**
  * Configuration subroutine
**/
void configureSensor(void)
{
   if (began)
  {
    while (!bno.isFullyCalibrated())
    {
      uint8_t sys = 0;
      uint8_t gyro = 0;
      uint8_t accel = 0;
      uint8_t mag = 0;

      // Get Calibration information
      bno.getCalibration(&sys, &gyro, &accel, &mag);

      // Print out the Calibration values to Serial if full callibration
      // hasn't been achieved
      Serial.print("CALIBRATION: Sys=");
      Serial.print(sys, DEC);
      Serial.print(" Gyro=");
      Serial.print(gyro, DEC);
      Serial.print(" Accel=");
      Serial.print(accel, DEC);
      Serial.print(" Mag=");
      Serial.println(mag, DEC);

      delay(100);
    }
  }
}

/**
  * Setup subroutine
**/
void setup()
{
  //Set the serial output baud rate
  Serial.begin(115200);

  // Configure the UART pins as well as the Teensy on-board LED
  pinMode(rxPin, INPUT);
  pinMode(txPin, OUTPUT);
  pinMode(13, OUTPUT);

  // Start the configured BNO055
  began = bno.begin();

  // Configure subroutine
  configureSensor();

  // Configure the Bluetooth baud rate and put the device in command mode
  btSerial.begin(115200);
  delay(100);

}

/**
  * Main loop
**/
void loop()
{
      // String that will store a json packet to be sent over BT
      String outputToBT = "";

      //Initialize JSON packet
      StaticJsonBuffer<256> jsonBuffer;
      JsonObject& root = jsonBuffer.createObject();
  
      // Only send information to Android phone if device is fully
      // configured
      if (bno.isFullyCalibrated())
      {
          // Light up the LED because the BNO055 is successfully configured
          digitalWrite(13, HIGH);

          // Receive event object from BNO055
          sensors_event_t event;
          bno.getEvent(&event);

          // Create a JSON packet to send
          root["status"] = true;
          root["x"] = event.orientation.x;
          root["y"] = event.orientation.y;
          root["z"] = event.orientation.z;

          // Write new json packet to a string
          root.printTo(outputToBT);

          // Send the json packet to the Bluetooth Mate
          btSerial.print(outputToBT);
          
          // Print the json packet to serial output to display on a connected computer
          Serial.print(outputToBT + "\n");
        }
        else
        {
          // Turn off the LED, recall the configuration subroutine
          digitalWrite(13, LOW);

          // Create a JSON packet to send
          root["status"] = false;
          root["x"] = -1.0;
          root["y"] = -1.0;
          root["z"] = -1.0;

          // Write new json packet to a string
          root.printTo(outputToBT);

          // Send the json packet to the Bluetooth Mate
          btSerial.print(outputToBT);
          
          configureSensor();
        }
        
      // Send a packet every ~500 ms
      delay(500);
}
