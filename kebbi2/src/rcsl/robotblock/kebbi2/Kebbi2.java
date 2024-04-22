package rcsl.robotblock.kebbi2;

import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailList;
import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.ComponentConstants;
import com.google.appinventor.components.runtime.*;

import androidx.annotation.RequiresPermission;

import com.nuwarobotics.service.agent.NuwaRobotAPI;
import com.nuwarobotics.service.agent.VoiceEventListener;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.ParseException;
import android.widget.Toast;
import java.util.Locale;

public class Kebbi2 extends AndroidNonvisibleComponent implements IRobotEventCallback, IVoiceEventCallback, OnStopListener {

  private Context context;
  final Component thisComponent = this;
  ExecutorService newSingleThreadPool;

  private NuwaRobotAPI mRobot;
  private ArrayList<String> cmdTTS;
  private ArrayList<String> cmdMotion;
  private String TTSLanguage;
  private Handler mHandler;

  private boolean mTts_complete = true;
  private boolean mMotion_complete = true;
  private boolean doTTSnMotion = true;
  // private boolean do_TTS_and_action = true;
  private int TnMCmdStep = 0 ;
  private int mCmdStep = 0 ;
  int motorID;  //for motor control
  float positionInDegree, speedInDegreePerSec;  //for motor control
  String speech_rate = "";

  boolean isEnableOnStop = true;

  BindBackgroundService bindBackgroundService;

  // movement initial
  private ArrayList<Float> movelist;
  private ArrayList<Float> turnlist;
  private ArrayList<Integer> moveTimeList;
  private HandlerThread handlerThread;
  private Handler mWorkerHandler;
  private boolean move = true;

  public Kebbi2(ComponentContainer container) {
    super(container.$form());
   
    context = container.$form();

    // if(GetLocalDate()){
      // Toast.makeText(context, "hi there", Toast.LENGTH_SHORT).show();
      form.registerForOnStop(this);

      InitialKebbi();

      cmdTTS = new ArrayList<String>();
      cmdMotion = new ArrayList<String>();
      mHandler = new Handler();  //create a Handler based on Main Thread(UI Thread)
      newSingleThreadPool = Executors.newSingleThreadExecutor();

      // map.put("English (United States)", "en_US");
		  // map.put("Chinese (Traditional Han,Taiwan)", "zh_TW");

      // bindBackgroundService = new BindBackgroundService(context);
      // bindBackgroundService.BindBGService();

      // movement initial
      movelist = new ArrayList<Float>();
      turnlist = new ArrayList<Float>();
      moveTimeList = new ArrayList<Integer>();

      initMovementHandler();
    // }else{
      // Toast.makeText(context, "the extension has expired.", Toast.LENGTH_LONG).show();
    // }
  }

  @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
  @SimpleFunction(description = "Start Kebbi service")
  public void StartKebbiService(){
    newSingleThreadPool.execute(new Runnable() {
        @Override
        public void run() {
            InitialKebbi();
        }
    });
  }

  public void InitialKebbi() {
    if(mRobot == null){
      mRobot = KebbiInstance.getInstance(context);
      RobotEvent.robotEventCallback = this;
      VoiceEvent.voiceEventCallback = this;
      new RobotEvent(mRobot);
      new VoiceEvent(mRobot);
    }
  }

  private void initMovementHandler(){
    handlerThread = new HandlerThread("Message Handler");
    handlerThread.start();
    mWorkerHandler = new Handler(handlerThread.getLooper());
  }

  @SimpleFunction(description = "To control Robot to forward, backwards and turns. Where \n \"moveOrTurn\" is to control Kebbi direction\n\"(float)speed\" range: max: 0.2(Meter/sec) go forward, min: -0.2(Meter/sec) go back; max: 30(Degree/sec) turn left, min: -30(Degree/sec) turn right\n\"time(second)\" means movement time of Kebbi")
  public void Movement(String moveOrTurn, float speed, int time) {
    if(moveOrTurn.equals("move")){
      movelist.add((float)speed);
      turnlist.add(0f);
    }else if(moveOrTurn.equals("turn")){
      movelist.add(0f);
      turnlist.add((float)speed);
    }
    moveTimeList.add(time*1000);

    if(move) {
      mWorkerHandler.post(moveRunnable);
      move = false;
    }
  }

  Runnable stopMoveRunnable=new Runnable(){
    @Override
    public void run() {
      try{
        mRobot.move(0);
        mRobot.turn(0);
        list_index++;
        if(list_index < movelist.size()) {
            //both TTS and Motion complete, we play next action
            // mHandler.post(robotTTSandAction);//play next action
            mWorkerHandler.post(moveRunnable);
        }else{
            list_index = 0;
            move = true;
            movelist.clear();
            turnlist.clear();
            moveTimeList.clear();
        }
      }catch (IndexOutOfBoundsException indexOutOfBoundsException){
        return;
      }
    }
  };

  int list_index = 0;
  float current_move = 0f;
  float current_turn = 0f;
  Runnable moveRunnable=new Runnable() {
    @Override
    public void run() {
      current_move = movelist.get(list_index);
      current_turn = turnlist.get(list_index);

      if(current_move == 0){
          mRobot.turn(current_turn);
      }else{
          mRobot.move(current_move);
      }
      
      mWorkerHandler.postDelayed(stopMoveRunnable, moveTimeList.get(list_index));
    }
  };

  @SimpleProperty(description = "String: Speech rate is on a scale of 1-9")
  public String SpeechRate() {
      return speech_rate;
  }

  @DesignerProperty(editorType = "choices", defaultValue = "5", editorArgs = {"1", "2", "3", "4", "5", "6", "7", "8", "9"})
  @SimpleProperty(description = "String: Speech rate is on a scale of 1-9")
  public void SpeechRate(String value) {
      speech_rate = value.trim();
      mRobot.setSpeakParameter(VoiceEventListener.SpeakType.NORMAL, "speed", speech_rate); // 1~9
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "True")
  @SimpleProperty(description = "enable onStop lifecycle")
  public void EnableOnStop(boolean value) {
      isEnableOnStop = value;
  }

  int[] colorful = new int[4];
  public int[] DecodeColor(int colors){
    colorful[0] = (colors >> 24) & 0xff; // or color >>> 24  brightness
    colorful[1] = (colors >> 16) & 0xff;  //R
    colorful[2] = (colors >>  8) & 0xff;  //G
    colorful[3] = (colors      ) & 0xff;  //B

    return colorful;
  }

  @SimpleProperty(description = "")
  public void TheWholeBodyGlowsIn(int colors) {
      int[] color = DecodeColor(colors);

      for(int i = 1; i <=4; i++ ){
        mRobot.setLedColor(i, color[0], color[1], color[2], color[3]);
        // turn on LED
        mRobot.enableLed(i, 1);
      }
  }

  @SimpleProperty(description = "")
  public void HeadGlowsIn(int colors) {
      SetLEDColor(colors, 1);
  }

  @SimpleProperty(description = "")
  public void ChestGlowsIn(int colors) {
      SetLEDColor(colors, 2);
  }

  @SimpleProperty(description = "")
  public void LeftHandGlowsIn(int colors) {
      SetLEDColor(colors, 4);
  }

  @SimpleProperty(description = "")
  public void RightHandGlowsIn(int colors) {
      SetLEDColor(colors, 3);
  }

  private void SetLEDColor(int colors, int bodypart){
      int[] color = DecodeColor(colors);
      mRobot.setLedColor(bodypart, color[0], color[1], color[2], color[3]);
      // // turn on LED
      mRobot.enableLed(bodypart, 1);
  }

  @SimpleFunction(description = "LED be controlled by App")
  public void TheAppControlsLED(){
     mRobot.disableSystemLED();
  }

  @SimpleFunction(description = "LED be controlled by Robot itself")
  public void TheRobotControlsLED(){
     mRobot.enableSystemLED();
  }

  // @SimpleFunction(description = "The unit of time is second")
  // public void WaitFor(int seconds){
  //   try{
  //     int milliseconds = seconds * 1000;
  //     Thread.sleep(milliseconds);
  //   } catch (Exception e) {
  //     e.printStackTrace();
  //   }

    // newSingleThreadPool.execute(new Runnable() {
    //   @Override
    //   public void run() {
    //     try{
    //       int milliseconds = seconds * 1000;
    //       Thread.sleep(milliseconds);
    //     } catch (Exception e) {
    //       e.printStackTrace();
    //     }
    //   }
    // });
  // }

  @SimpleFunction(description = "Kebbi has 10 motors, you can control each of them")
  public void MotorControl(int motorID, float positionInDegree, float speedInDegreePerSec) {
    // this.motorID = motorID;
    // this.positionInDegree = positionInDegree;
    // this.speedInDegreePerSec = speedInDegreePerSec;
    // DoTTSandAction(sentence, "custom_motion");
    Log.d("motor control", "motor control");

    mRobot.ctlMotor(motorID, positionInDegree, speedInDegreePerSec);

    // if(sentence != ""){
    //   mRobot.startTTS(sentence);
    //   VoiceEvent.tts_sentence = sentence;
    // }
  }

  @SimpleFunction(description = "Kebbi has 10 motors, you can control each of them")
  public void MotorControlWithTTSLanguage(String sentence, String language, int motorID, float positionInDegree, float speedInDegreePerSec) {
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        // this.motorID = motorID;
        // this.positionInDegree = positionInDegree;
        // this.speedInDegreePerSec = speedInDegreePerSec;
        mRobot.startTTS(sentence, language);
        mRobot.ctlMotor(motorID, positionInDegree, speedInDegreePerSec);
      }
    });
    thread.start();
    // DoTTSandAction(sentence, "custom_motion");
  }

  @SimpleFunction(description = "Kebbi motion reset")
  public void MotionReset() {
    mRobot.motionReset();
  }

  @SimpleFunction(description = "Kebbi STT")
  public void StartSTT() {
    Log.d("STT", "STT");
    mRobot.startSpeech2Text(false);
  }

  @SimpleFunction(description = "Kebbi speaks out a sentence by a giving string.")
  public void Say(String sentence) {
    if(sentence != ""){
      DoTTSandAction(sentence, "");
    }
  }

  @SimpleFunction(description = "Kebbi speaks out a sentence by a giving string.")
  public void Say(String sentence, String language) {
    if(sentence != ""){
      TTSLanguage = language;
      DoTTSLanguageAndAction(sentence, TTSLanguage, "");
    }
  }

  @SimpleFunction(description = "Kebbi stops TTS.")
  public void StopSaying(){
    mRobot.stopTTS();
  }

  @SimpleFunction()
  public void StopTTSandRecognize() {
    mRobot.stopTTSandRecognize();
  }

  @SimpleFunction(description = "Kebbi does an action.")
  public void DoMotion(String motion) {
    DoTTSandAction("", motion);
  }

  @SimpleFunction(description = "Kebbi does TTS and action.")
  public void DoTTSandAction(String sentence, String motion) {
  //  mHandler.post(new Runnable() {
  //     public void run() {
        cmdTTS.add(sentence);
        cmdMotion.add(motion);

        if(doTTSnMotion) {
          // mHandler.post(robotTTSandAction);
          // newSingleThreadPool.execute(robotTTSandAction);
          AsynchUtil.runAsynchronously(robotTTSandAction);
          doTTSnMotion = false;
        }
    //  }
    // });
  }

  @SimpleFunction(description = "Kebbi does TTS and action.")
  public void DoTTSLanguageAndAction(String sentence, String language, String motion) {
    cmdTTS.add(sentence);
    cmdMotion.add(motion);
    TTSLanguage = language;

    if(doTTSnMotion) {
      // mHandler.post(robotTTSandAction);
      newSingleThreadPool.execute(robotTTSLanguageAndAction);
      doTTSnMotion = false;
    }
  }

  Runnable robotTTSandAction = new Runnable() {
    public void run() {
      try {
        String current_tts = cmdTTS.get(TnMCmdStep);
        String current_motion = cmdMotion.get(TnMCmdStep);

        if(!current_tts.equals("")) mTts_complete = false;
        if(!current_motion.equals("")) mMotion_complete = false;

        VoiceEvent.tts_sentence = current_tts;
        //Start play tts and motion if need
        if(!current_tts.equals("")) mRobot.startTTS(current_tts);
        if(!current_motion.equals("")) mRobot.motionPlay(current_motion, false);
        //Please NOTICE that auto_fadein should assign false when motion file nothing to display
        // if(current_motion.equals("custom_motion")){
        //   mRobot.ctlMotor(motorID, positionInDegree, speedInDegreePerSec);
        //   mMotion_complete = true;
        // }else if(!current_motion.equals("")) mRobot.motionPlay(current_motion, false);

        while(!mTts_complete || !mMotion_complete) {
            //wait both action complete
            Log.d("wait","wait both action complete");
        }
        TnMCmdStep++;
        if(TnMCmdStep < cmdMotion.size()) {
            //both TTS and Motion complete, we play next action
            // mHandler.post(robotTTSandAction);//play next action
            newSingleThreadPool.execute(robotTTSandAction);
        }else{
            TnMCmdStep = 0;
            doTTSnMotion = true;
            mTts_complete = true;
            mMotion_complete = true;
            cmdMotion.clear();
            cmdTTS.clear();
            // mRobot.motionReset();//Reset Robot pose to default
        }  
      }catch (IndexOutOfBoundsException indexOutOfBoundsException){
        return;
      }     
    }
  };

  Runnable robotTTSLanguageAndAction = new Runnable() {
    public void run() {
      try {
        String current_tts = cmdTTS.get(TnMCmdStep);
        String current_motion = cmdMotion.get(TnMCmdStep);

        if(!current_tts.equals("")) mTts_complete = false;
        if(!current_motion.equals("")) mMotion_complete = false;

        VoiceEvent.tts_sentence = current_tts;
        //Start play tts and motion if need
        if(!current_tts.equals("")) mRobot.startTTS(current_tts, TTSLanguage);
        if(!current_motion.equals("")) mRobot.motionPlay(current_motion, false);
        //Please NOTICE that auto_fadein should assign false when motion file nothing to display
        // if(current_motion.equals("custom_motion")){
        //   mRobot.ctlMotor(motorID, positionInDegree, speedInDegreePerSec);
        //   mMotion_complete = true;
        // }else if(!current_motion.equals("")) mRobot.motionPlay(current_motion, false);

        while(!mTts_complete || !mMotion_complete) {
            //wait both action complete
            Log.d("wait","wait both action complete");
        }
        TnMCmdStep++;
        if(TnMCmdStep < cmdMotion.size()) {
            //both TTS and Motion complete, we play next action
            // mHandler.post(robotTTSandAction);//play next action
            newSingleThreadPool.execute(robotTTSandAction);
        }else{
            TnMCmdStep = 0;
            doTTSnMotion = true;
            mTts_complete = true;
            mMotion_complete = true;
            cmdMotion.clear();
            cmdTTS.clear();
            // mRobot.motionReset();//Reset Robot pose to default
        }  
      }catch (IndexOutOfBoundsException indexOutOfBoundsException){
        return;
      }     
    }
  };

  @SimpleFunction(description = "Release Nuwa SDK resource while App closed.")
  public void Release() {
    AsynchUtil.runAsynchronously(new Runnable() {
      public void run() {
        if(mRobot.isKiWiServiceReady()) {
          // newSingleThreadPool.shutdown();
          mRobot.release();
          if(Build.VERSION.SDK_INT>=16 && Build.VERSION.SDK_INT<21){
            form.finishAffinity();
          } else if(Build.VERSION.SDK_INT>=21){
            form.finishAndRemoveTask();
          }
        }
      }
    });
  }

  @Override
  public void onStartOfMotionPlay(String s) {
    // bindBackgroundService.DisableTrack();
  }

  @Override
  @SimpleEvent(description = "motion complete event")
  public void onCompleteOfMotionPlay(String s) {
    mMotion_complete = true;

    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "onCompleteOfMotionPlay", s);
      }
    });    

    // bindBackgroundService.EnableTrack();
  }

  @Override
  @SimpleEvent(description = "PIR sensor event")
  public void onPIREvent(int i) {
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "onPIREvent", i);
      }
    });
  }

  @SimpleFunction(description = "call onTap event")
  public void EventOnTap(int bodypart) {
    onTap(bodypart);
  }

  @Override
  @SimpleEvent(description = "tap event")
  public void onTap(int bodyPart){
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "onTap", bodyPart);
      }
    });
  }

  // public void onTap(int bodyPart) {
  //   mHandler.post(new Runnable() {
  //     public void run() {
  //       switch(bodyPart){
  //         case 1:
  //           onTapHead();
  //           break;
  //         case 2:
  //           onTapChest();
  //           break;
  //         case 3:
  //           onTapRightHand();
  //           break;
  //         case 4:
  //           onTapLeftHand();
  //           break;
  //         case 5:
  //           onTapLeftFace();
  //           break;
  //         case 6:
  //           onTapRightFace();
  //           break;
  //       }
  //     }
  //   });
  // }

  // @SimpleEvent(description = "")
  // public void onTapHead(){
  //   EventDispatcher.dispatchEvent(thisComponent, "onTapHead");
  // }

  // @SimpleEvent(description = "")
  // public void onTapChest(){
  //   EventDispatcher.dispatchEvent(thisComponent, "onTapChest");
  // }

  // @SimpleEvent(description = "")
  // public void onTapLeftHand(){
  //   EventDispatcher.dispatchEvent(thisComponent, "onTapLeftHand");
  // }

  // @SimpleEvent(description = "")
  // public void onTapRightHand(){
  //   EventDispatcher.dispatchEvent(thisComponent, "onTapRightHand");
  // }

  // @SimpleEvent(description = "")
  // public void onTapLeftFace(){
  //   EventDispatcher.dispatchEvent(thisComponent, "onTapLeftFace");
  // }

  // @SimpleEvent(description = "")
  // public void onTapRightFace(){
  //   EventDispatcher.dispatchEvent(thisComponent, "onTapRightFace");
  // }

  // Voice callback
  @Override
  @SimpleEvent(description = "trigger this event when Text To Speech is finished.")
  public void TTSCompleted(String sentence){
    mTts_complete = true;
    mHandler.post(new Runnable() {
      public void run() {
          EventDispatcher.dispatchEvent(thisComponent, "TTSCompleted", sentence);
      }
    });
  }

  @Override
  @SimpleEvent()
  public void STTCompleted(boolean b, String result, String sentence){
    mHandler.post(new Runnable() {
      public void run() {
          EventDispatcher.dispatchEvent(thisComponent, "STTCompleted", b, result, sentence);
      }
    });
  }

  public void onStop() {
    if(isEnableOnStop){
      // newSingleThreadPool.shutdown();

      mRobot.stopTTS();
      mRobot.stopTTSandRecognize();
      cmdMotion.clear();
      cmdTTS.clear();    

      handlerThread.quit();
      mRobot.release();

      Log.d("onStop","release");
      if(Build.VERSION.SDK_INT>=16 && Build.VERSION.SDK_INT<21){
        form.finishAffinity();
        Log.d("onStop","finishAffinity");
      } else if(Build.VERSION.SDK_INT>=21){
        form.finishAndRemoveTask();
        Log.d("onStop","finishAndRemoveTask");
      }
    }
  }

  @SimpleFunction()
  public void TurnOffLeftHandLED(){
    mRobot.enableLed(4, 0);
  }

  @SimpleFunction()
  public void TurnOffRightHandLED(){
    mRobot.enableLed(3, 0);
  }

  @SimpleProperty(description = "Kebbi's head")
  public int Head() {
    return 1;
  }

  @SimpleProperty(description = "Kebbi's chest")
  public int Chest() {
    return 2;
  }

  @SimpleProperty(description = "Kebbi's right hand")
  public int RightHand() {
    return 3;
  }

  @SimpleProperty(description = "Kebbi's left hand")
  public int LeftHand() {
    return 4;
  }

  @SimpleProperty(description = "Kebbi's left face")
  public int LeftFace() {
    return 5;
  }

  @SimpleProperty(description = "Kebbi's right face")
  public int RightFace() {
    return 6;
  }

  @SimpleProperty(description = "Forwarding or backwarding control of Kebbi")
  public String Move() {
    return "move";
  }

  @SimpleProperty(description = "Turning control of Kebbi")
  public String Turn() {
    return "turn";
  }

  @SimpleProperty(description = "TTS Language ENGLISH")
  public String ENGLISH() {
    return Locale.ENGLISH.toString();
  }

  @SimpleProperty(description = "TTS Language CHINESE")
  public String CHINESE() {
    return Locale.CHINESE.toString();
  }

  private boolean GetLocalDate(){
    SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd"); 
    try {
      
      Date date = new Date();  
      
      Date date1 = sdformat.parse("2022-05-18");
      // Date date2 = sdformat.parse("2019-09-16");

      return date1.after(sdformat.parse(sdformat.format(date)));
    } catch (ParseException ex) {
      return false;
    }
  }
}
