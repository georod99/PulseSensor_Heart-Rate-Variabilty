import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.serial.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class PulseSensorAmped_HRV_BreathingPrompt extends PApplet {

/*     PulseSensor Amped HRV Biofeedback v1.1.0

This is an HRV visualizer code for Pulse Sensor.  www.pulsesensor.com
Use this with PulseSensorAmped_Arduino_1.5.0 code and the Pulse Sensor Amped hardware.
This code will track Heart Rate Variabilty by tracing the changes in IBI values
over the frequency domain. The code looks for a change in the trend of IBI values,
up or down, and calculates the frequency using the last peak or trough.
Thus deriving the IBI frequency based on 1/2 wave data.

key press commands included in this version:
  press 'S' to take a picture of the data window. (JPG image saved in Sketch folder)
  press 'C' to refresh the graph
  press 'W' to show or hide the IBI waveform (as seen in Time Domain example)
Created by Joel Murphy, Spring 2018
Updated Summer 2013 for efficiency and readability
This code released into the public domain without promises that it will work for your intended application.
Or that it will work at all, for that matter. I hereby disclaim.
*/

  // serial library lets us talk to Arduino
PFont font;
PFont portsFont;
Serial port;

float freq;               // used to hold derived HRV frequency
float runningTotal = 1;   // can't initialize as 0 because math
float mean;               // useful for VLF derivation...........
int IBI;                  // length of time between heartbeats in milliseconds (sent from Arduino, updated in serialEvent)
int P;                    // peak value of IBI waveform
int T;                    // trough value of IBI waveform
int amp;                  // amplitude of IBI waveform
int lastIBI;              // value of the last IBI sent from Arduino
int[] PPG;                // array of raw Pulse Sensor datapoints
int[] beatTime;           // array of IBI values to graph on Y axis
float[] powerPointX;      // array of power spectrum power points
float[] powerPointY;      // used to plot on screen
int pointCount = 0;       // used to help fill the powerPoint arrays
int windowWidth = 550;    // width of IBI data window
int windowHeight = 550;   // height of IBI data window
int pulseX = 715;         // left edge of rectangle for pulse window
int ibiX = 350;           // left edge of rectangle for IBI window

int breathCenterX = 995;
int breathCenterY;
float breathXmax = 315.0f;
float breathXmin = 35.0f;
float breathYmax = 225.0f;
float breathYmin = 25.0f;
float breathX = breathXmin;
float breathY = breathYmin;
float breathXstep = (breathXmax-breathXmin)/180.0f;
float breathYstep = (breathYmax-breathYmin)/180.0f;
float blue = 17.0f;
float red = 180.0f; //197;
float fadeValue = blue;
float breathCycle = 6.0f;

float HRVdelta[];
boolean newDelta = false;
int deltaGraphHeight = 256;
int deltaGraphY;

// boolean fadeUp = true;
float angle;
float angleStep;
int counter = 0;

int eggshell = color(255, 253, 248);

String direction = "none";  // used in verbose feedback

// initializing flags
boolean pulse = false;    // made true in serialEvent when new IBI value sent from arduino
boolean first = true;     // try not to screw up if it's the first time
boolean showWave = true; // show or not show the IBI waveform on screen
boolean goingUp;          // used to keep track of direction of IBI wave

// SERIAL PORT STUFF TO HELP YOU FIND THE CORRECT SERIAL PORT
String serialPort;
String[] serialPorts = new String[Serial.list().length];
boolean serialPortFound = false;
Radio[] button = new Radio[Serial.list().length*2];
int numPorts = serialPorts.length;
boolean refreshPorts = false;

public void setup() {
                       // stage size
  frameRate(60);                     // frame rate
  font = loadFont("Arial-BoldMT-36.vlw");
  textFont(font);
  textAlign(CENTER);
  rectMode(CENTER);
  ellipseMode(CENTER);
  breathCenterY = height/4 + height/16;
  deltaGraphY = (height/4)*3;
  beatTime = new int[windowWidth];   // the beatTime array holds IBI graph data
  PPG = new int[150];                // PPG array that that prints heartbeat waveform
  powerPointX = new float[150];      // these arrays hold the power spectrum point coordinates
  powerPointY = new float[150];
  HRVdelta = new float[32];
  for(int i=0; i<32; i++){
    HRVdelta[i]= 1.0f;
  }
  // set data traces to default
  resetDataTraces();


background(0);
drawDataWindows();

// GO FIND THE ARDUINO
  fill(eggshell);
  text("Select Your Serial Port",350,50);
  listAvailablePorts();

// println(breathY);

}  // END OF SETUP


public void draw(){
if(serialPortFound){
  // ONLY RUN THE VISUALIZER AFTER THE PORT IS CONNECTED
   background(0);
//  DRAW THE BACKGROUND ELEMENTS AND TEXT
   noStroke();
   drawDataWindows();
   writeLabels();



//    UPDATE THE IBI ARRAY IF THERE HAS NEW DATA
//  when we get heartbeat data, try to find the latest HRV freq if it is available
  if (pulse == true){                           // check for new data from arduino
    pulse = false;                              // drop the pulse flag, it gets set in serialEvent

    if (IBI < lastIBI && goingUp == true){  // check for IBI wave peak
      goingUp = false;                 // now changing direction from up to down
      direction = "down";              // used in verbose feedback
      freq = (runningTotal/1000) *2;   // scale milliseconds to seconds account for 1/2 wave data
      freq = 1/freq;                   // convert time IBI trending up to Hz
      runningTotal = 0;                // reset this for next time
      amp = P-T;                       // measure the size of the IBI 1/2 wave that just happend
      mean = P-amp/2;                  // the average is useful for VLF derivation.......
      newDelta = true;
      T = lastIBI;                     // set the last IBI as the most recent trough cause we're going down
      powerPointX[pointCount] = map(freq,0,1.2f,75,windowWidth+75);  // plot the frequency
      powerPointY[pointCount] = height-(35+amp);  // amp determines 'power' of signal
      pointCount++;                    // build the powerPoint array
      if(pointCount == 150){pointCount = 0;}      // overflow the powerPoint array
    }

    if (IBI > lastIBI && goingUp == false){  // check for IBI wave trough
      goingUp = true;                  // now changing direction from down to up
      direction = "up";                // used in verbose feedback
      freq = (runningTotal/1000) * 2;  // scale milliseconds to seconds, account for 1/2 wave data
      freq = 1/freq;                   // convert time IBI trending up to Hz
      runningTotal = 0;                // reset this for next time
      amp = P-T;                       // measure the size of the IBI 1/2 wave that just happend
      mean = P-amp/2;                  // the average is useful for VLF derivation.......
      newDelta = true;
      P = lastIBI;                     // set the last IBI as the most recent peak cause we're going up
      powerPointX[pointCount] = map(freq,0,1.2f,75,windowWidth+75);  // plot the frequency
      powerPointY[pointCount] = height-(35+amp); // amp determines 'power' of signal
      pointCount++;                    // build the powerPoint array
      if(pointCount == 150){pointCount = 0;}      // overflow the powerPoint array
    }

    if (IBI < T){                        // T is the trough
      T = IBI;                         // keep track of lowest point in pulse wave
    }
    if (IBI > P){                        // P is the trough
      P = IBI;                         // keep track of highest point in pulse wave
    }
    lastIBI = IBI;                     // keep track to measure the trend
    runningTotal += IBI;               // how long since IBI wave changed direction?

//  >>> this part is for IBI waveform graphing
    for (int i=0; i<beatTime.length-3; i+=3){   // shift the data in beatTime array 3 pixels left
      beatTime[i] = beatTime[i+3];              // shift the data points through the array
    }
      IBI = constrain(IBI,300,1200);            // don't let the new IBI value escape the data window!
      beatTime[beatTime.length-1] = IBI;        // update the current IBI

 }

//    DRAW THE SPECTRUM PLOT
  String F = nf(freq,1,3);  // format the freq variable so it's nice to print
  fill(128);
  text(F+"Hz",200,100);     // print the freq variable for verbose feedback
  // first, make a vertical line at the most recent plot point in red
  stroke(250,5,180);
  line(map(freq,0,1.2f,75,windowWidth+75),height-35,map(freq,0,1.2f,75,windowWidth+75),(height-(35+amp)));
  stroke(255,0,0);
  for(int i=0; i<150; i++){
    ellipse(powerPointX[i],powerPointY[i],2,2);    // plot a history of data points in red
  }



  //    DRAW THE IBI PLOT
  if(showWave){
    stroke(0,0,255);                                // IBI graph in blue
    noFill();
    beginShape();                                   // use beginShape to draw the graph
    for (int i=0; i<=beatTime.length-1; i+=3){      // set a datapoint every three pixels
      float  y = map(beatTime[i],300,1200,615,65);  // invert and scale data so it looks normal
      vertex(i+75,y);                               // set the vertex coordinates
    }
    endShape();                                     // connect the vertices
    noStroke();
    fill(250,0,0);                                  // draw the current data point as a red dot for fun
    float  y = map(beatTime[beatTime.length-1],300,1200,615,65);  // invert and scale data so it looks normal
    ellipse(windowWidth+75,y,6,6);                  // draw latest data point as a red dot
    fill(255,253,248);                              // eggshell white
    text("IBI: "+IBI+"mS", pulseX-25,50); // )-85,50);             // print latest IBI value above pulse wave window
    noFill();


  //   GRAPH THE LIVE PULSE SENSOR DATA
  // stroke(250,0,0);                         // use red for the pulse wave
  // for (int i=1; i<PPG.length-1; i++){      // draw the waveform shape
  //   line(width-160+i,PPG[i],width-160+(i-1),PPG[i-1]);
  // }
   //   GRAPH THE PULSE SENSOR DATA
   stroke(250,0,0);                                       // use red for the pulse wave
    beginShape();                                         // beginShape is a nice way to draw graphs!
    for (int i=1; i<PPG.length-1; i++){                   // scroll through the PPG array
      float x = 640+i; //width-160+i;
      y = PPG[i];
      vertex(x,y);                                        // set the vertex coordinates
    }
    endShape();
  }

  // GRAPH THE HRV DELTA IN BPM
  noStroke();
  fill(128);
  if(newDelta){
    for(int i=HRVdelta.length-1; i>0; i--){
      HRVdelta[i] = HRVdelta[i-1];
    }
    HRVdelta[0] = amp;
    newDelta = false;
  }
  int deltaCounter = 0;
  for(int i=0; i<deltaGraphHeight; i+=8){
    rect(breathCenterX,(deltaGraphY+deltaGraphHeight/2)-i-4,HRVdelta[deltaCounter],8);
    deltaCounter++;
  }

  } else { // SCAN BUTTONS TO FIND THE SERIAL PORT

    autoScanPorts();

    if(refreshPorts){
      refreshPorts = false;
      drawDataWindows();
  //    drawHeart();
      listAvailablePorts();
    }

    for(int i=0; i<numPorts+1; i++){
      button[i].overRadio(mouseX,mouseY);
      button[i].displayRadio();
    }
  }
       // breathing prompt
  angle = radians(((millis()/1000.0f)/breathCycle)*360);
  breathX = 35 + (sin(angle) * breathXmax/2) + breathXmax/2;
  breathY = 25 + (sin(angle) * breathYmax/2) + breathYmax/2;
  fadeValue = blue + (sin(angle) * red/2) + red/2;
  noStroke();
  fill(fadeValue,105,206);
  ellipse(breathCenterX,breathCenterY,breathX,breathY);

}  //end of draw loop


public void drawDataWindows(){
  noStroke();
  fill(eggshell);                                        // eggshell white
  rect(ibiX,height/2+15,windowWidth,windowHeight);       // draw IBI data window
  rect(pulseX,(height/2)+15,150,550);                    // draw the pulse waveform window
  // ellipse(breathCenterX,breathCenterY,breathXmax,breathYmax); //350,250);        // breathing prompt
  rect(breathCenterX,deltaGraphY,380,deltaGraphHeight);                // amplitiude graph

  fill(200);                                                 // print scale values in grey
  for (int i=300; i<=1200; i+=450){                          // print Y axis scale values
    text(i, 40,map(i,300,1200,615,75));
  }
  for (int i=30; i<=180; i+=30){                             // print X axis scale values
    text(i, map(i,180,0,75,windowWidth+75),height-10);
  }
  stroke(200,10,250);                       // draw Y gridlines in purple, for fun
  for (int i=300; i<=1200; i+=100){         // draw Y axis lines with 100mS resolution
    line(75,map(i,300,1200,614,66),85,map(i,300,1200,614,66)); //grid the Y axis
  }
  for (int i=0; i<=180; i+=10){             // draw X axis lines with 10 beat resolution
    line(map(i,0,180,625,75),height-35,map(i,0,180,625,75),height-45); // grid the X axis
  }
  noStroke();
}

public void writeLabels(){
  fill(eggshell);
  text("Pulse Sensor Breathing Feedback",ibiX,40);              // title
  // fill(200);
  text("IBI", 40,200);                          // Y axis label
  // text("mS",40,230);
  text("Beats", 75+windowWidth-25, height-10);  // X axis advances 3 pixels with every beat
  text("Breath Cycle " + breathCycle + "s",breathCenterX,40);
  text(HRVdelta[0] + " IBI delta", breathCenterX,height-10);
}

public void listAvailablePorts(){
  println(Serial.list());    // print a list of available serial ports to the console
  serialPorts = Serial.list();
  fill(0);
  textFont(font,16);
  textAlign(LEFT);
  // set a counter to list the ports backwards
  int xPos = 150;
  int yPos = 0;
  for(int i=numPorts-1; i>=0; i--){
    button[i] = new Radio(xPos, 130+(yPos*20),12,color(180),color(80),color(255),i,button);
    text(serialPorts[i],xPos+15, 135+(yPos*20));
    yPos++;
    if(yPos > height-30){
      yPos = 0; xPos+=100;
    }
  }
  int p = numPorts;
  fill(233,0,0);
  button[p] = new Radio(xPos, 130+(yPos*20),12,color(180),color(80),color(255),p,button);
  text("Refresh Serial Ports List",xPos+15, 135+(yPos*20));

  textFont(font);
  textAlign(CENTER);
}

public void autoScanPorts(){
 if(Serial.list().length != numPorts){
   if(Serial.list().length > numPorts){
     println("New Ports Opened!");
     int diff = Serial.list().length - numPorts;	// was serialPorts.length
     serialPorts = expand(serialPorts,diff);
     numPorts = Serial.list().length;
   }else if(Serial.list().length < numPorts){
     println("Some Ports Closed!");
     numPorts = Serial.list().length;
   }
   refreshPorts = true;
   return;
 }
}

public void resetDataTraces(){
  // reset IBI trace
  for(int i=0; i<beatTime.length; i++){
    beatTime[i] = 750;              // initialize the IBI graph with data line at midpoint
  }
  // reset PPG trace
  for (int i=0; i<=PPG.length-1; i++){
   PPG[i] = height/2+15;             // initialize PPG widow with data line at midpoint
  }
  // reset power point coordinates
  for (int i=0; i<150; i++){       // startup values place the coordinates at the bottom right corner
    powerPointX[i] = 625;
    powerPointY[i] = height - 35;
  }
}

public void mousePressed(){
  if(!serialPortFound){
    for(int i=0; i<=numPorts; i++){
      if(button[i].pressRadio(mouseX,mouseY)){
        if(i == numPorts){
          if(Serial.list().length > numPorts){
            println("New Ports Opened!");
            int diff = Serial.list().length - numPorts;	// was serialPorts.length
            serialPorts = expand(serialPorts,diff);
            //button = (Radio[]) expand(button,diff);
            numPorts = Serial.list().length;
          }else if(Serial.list().length < numPorts){
            println("Some Ports Closed!");
            numPorts = Serial.list().length;
          }else if(Serial.list().length == numPorts){
            return;
          }
          refreshPorts = true;
          return;
        }else

        try{
          port = new Serial(this, Serial.list()[i], 115200);  // make sure Arduino is talking serial at this baud rate
          delay(1000);
          println(port.read());
          port.clear();            // flush buffer
          port.bufferUntil('\n');  // set buffer full flag on receipt of carriage return
          serialPortFound = true;
        }
        catch(Exception e){
          println("Couldn't open port " + Serial.list()[i]);
          fill(255,0,0);
          textFont(font,16);
          textAlign(LEFT);
          text("Couldn't open port " + Serial.list()[i],165,90);
          textFont(font);
          textAlign(CENTER);
        }
      }
    }
  }
}

public void mouseReleased(){
}

public void keyPressed(){

 switch(key){
   case 's':    // pressing 's' or 'S' will take a jpg of the processing window
   case 'S':
     saveFrame("HRV-####.jpg");      // take a shot of that!
     break;
  // clear the screen when you press 'R' or 'r'
   case 'r':
   case 'R':
     resetDataTraces();
     break;
   case 'W':
   case 'w':
     showWave = !showWave;
     break;
   case '1':
     breathCycle = 4.0f;
     break;
   case '2':
     breathCycle = 4.5f;
     break;
   case '3':
     breathCycle = 5.0f;
     break;
   case '4':
     breathCycle = 5.5f;
     break;
   case '5':
     breathCycle = 6.0f;
     break;
   case '6':
     breathCycle = 6.5f;
     break;
   case '7':
     breathCycle = 7.0f;
     break;
   case '8':
     breathCycle = 7.5f;
     break;
   case '9':
     breathCycle = 8.0f;
     break;
   default:
     break;
 }
}


class Radio {
  int _x,_y;
  int size, dotSize;
  int baseColor, overColor, pressedColor;
  boolean over, pressed;
  int me;
  Radio[] radios;

  Radio(int xp, int yp, int s, int b, int o, int p, int m, Radio[] r) {
    _x = xp;
    _y = yp;
    size = s;
    dotSize = size - size/3;
    baseColor = b;
    overColor = o;
    pressedColor = p;
    radios = r;
    me = m;
  }

  public boolean pressRadio(float mx, float my){
    if (dist(_x, _y, mx, my) < size/2){
      pressed = true;
      for(int i=0; i<numPorts+1; i++){
        if(i != me){ radios[i].pressed = false; }
      }
      return true;
    } else {
      return false;
    }
  }

  public boolean overRadio(float mx, float my){
    if (dist(_x, _y, mx, my) < size/2){
      over = true;
      for(int i=0; i<numPorts+1; i++){
        if(i != me){ radios[i].over = false; }
      }
      return true;
    } else {
      over = false;
      return false;
    }
  }

  public void displayRadio(){
    noStroke();
    fill(baseColor);
    ellipse(_x,_y,size,size);
    if(over){
      fill(overColor);
      ellipse(_x,_y,dotSize,dotSize);
    }
    if(pressed){
      fill(pressedColor);
      ellipse(_x,_y,dotSize,dotSize);
    }
  }
}




public void serialEvent(Serial port){
try{
   String inData = port.readStringUntil('\n');  // read the ascii data into a String
   inData = trim(inData);                 // cut off white space (carriage return)

  if (inData.charAt(0) == 'S'){           // leading 'S' means Pulse Sensor data packet
     inData = inData.substring(1);        // cut off the leading 'S'
     int newPPG = PApplet.parseInt(inData);            // convert ascii string to integer
     for (int i = 0; i < PPG.length-1; i++){
       PPG[i] = PPG[i+1]; // move the Y coordinates of the pulse wave one pixel left
     }
      // new data enters on the right at pulseY.length-1
      // scale and constrain incoming Pulse Sensor value to fit inside the pulse window
      PPG[PPG.length-1] = PApplet.parseInt(map(newPPG,50,950,(height/2+15)+225,(height/2+15)-225));
     return;
   }

    if (inData.charAt(0) == 'Q'){         // leading 'Q' means IBI data packet
     inData = inData.substring(1);        // cut off the leading 'Q'
     IBI = PApplet.parseInt(inData);                   // convert ascii string to integer
     pulse = true;                        // set the pulse flag
     if (first){                          // if it's the first time, prime these variables
       lastIBI = IBI;
       P = IBI;
       T = IBI;
       first = false;
     }
     return;
    }
}catch(Exception e) {
   //println(e.toString());
}

}
  public void settings() {  size(1200,650); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "PulseSensorAmped_HRV_BreathingPrompt" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
