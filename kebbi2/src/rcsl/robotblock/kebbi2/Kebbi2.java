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

import com.nuwarobotics.service.IClientId;
import com.nuwarobotics.service.agent.NuwaRobotAPI;
import com.nuwarobotics.service.agent.RobotEventListener;
import com.nuwarobotics.service.agent.VoiceEventListener;

import static com.nuwarobotics.service.agent.NuwaRobotAPI.SENSOR_PIR;
import static com.nuwarobotics.service.agent.NuwaRobotAPI.SENSOR_TOUCH;
import com.nuwarobotics.service.agent.VoiceResultJsonParser;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Kebbi2 extends AndroidNonvisibleComponent implements RobotEventListener, VoiceEventListener, OnDestroyListener {

  private Context context;
  final Component thisComponent = this;
  ExecutorService newSingleThreadPool;

  private NuwaRobotAPI mRobot;
  private ArrayList<String> cmdTTS;
  private ArrayList<String> cmdMotion;
  private String current_motion;
  private String current_tts;

  private Handler mHandler;

  // private int mCmdStep = 0 ;
  private boolean mTts_complete = true;
  private boolean mMotion_complete = true;
  private boolean doTTSnMotion = true;
  // private boolean do_TTS_and_action = true;
  private int TnMCmdStep = 0 ;
  private int mCmdStep = 0 ;
  int motorID;  //for motor control
  float positionInDegree, speedInDegreePerSec;  //for motor control
  String stt_result = "";
  String speech_rate = "";
  String tts_sentence = "";

  public Kebbi2(ComponentContainer container) {
    super(container.$form());
    context = container.$form();

    form.registerForOnDestroy(this);
    
    cmdTTS = new ArrayList<String>();
    cmdMotion = new ArrayList<String>();
    mHandler = new Handler();  //create a Handler based on Main Thread(UI Thread)
    newSingleThreadPool = Executors.newSingleThreadExecutor();

    InitialKebbi();
  }

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
    IClientId id = new IClientId("rcsl.robotblock.kebbi2");
    mRobot = new NuwaRobotAPI(context, id);

    if(mRobot.isKiWiServiceReady()) mRobot.release();

    if(newSingleThreadPool.isShutdown()) newSingleThreadPool = Executors.newSingleThreadExecutor();
   
    mRobot.registerRobotEventListener(this);
    mRobot.registerVoiceEventListener(this);
  }

  @SimpleFunction(description = "Kebbi has 10 motors, you can control each of them")
  public void MotorControl(String sentence, int motorID, float positionInDegree, float speedInDegreePerSec) {
    this.motorID = motorID;
    this.positionInDegree = positionInDegree;
    this.speedInDegreePerSec = speedInDegreePerSec;
    DoTTSandAction(sentence, "custom_motion");
  }

  @SimpleFunction(description = "Kebbi motion reset")
  public void MotionReset() {
    mRobot.motionReset();
  }

  @SimpleFunction(description = "Kebbi STT")
  public void StartSTT() {
    try {
        Thread.sleep(500);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    mRobot.startSpeech2Text(false);
  }

  @SimpleFunction(description = "Kebbi speaks out a sentence by a giving string.")
  public void Say(String sentence) {
    DoTTSandAction(sentence, "");
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
    cmdTTS.add(sentence);
    cmdMotion.add(motion);

    if(doTTSnMotion) {
      // mHandler.post(robotTTSandAction);
      newSingleThreadPool.execute(robotTTSandAction);
      doTTSnMotion = false;
    }
  }

  Runnable robotTTSandAction = new Runnable() {
    public void run() {
      try {
        current_tts = cmdTTS.get(TnMCmdStep);
        current_motion = cmdMotion.get(TnMCmdStep);
        
        if(!current_tts.equals("")) mTts_complete = false;
        if(!current_motion.equals("")) mMotion_complete = false;

        tts_sentence = current_tts;
        //Start play tts and motion if need
        if(!current_tts.equals("")) mRobot.startTTS(current_tts);

        //Please NOTICE that auto_fadein should assign false when motion file nothing to display
        if(current_motion.equals("custom_motion")){
          mRobot.ctlMotor(motorID, positionInDegree, speedInDegreePerSec);
          mMotion_complete = true;
        }else if(!current_motion.equals("")) mRobot.motionPlay(current_motion, false);

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
          newSingleThreadPool.shutdown();
          mRobot.release();
        }
      }
    });
  }

  public void onWikiServiceStart() {
    mRobot.requestSensor(SENSOR_TOUCH);
    mRobot.requestSensor(SENSOR_PIR);
  }

  public void onWikiServiceStop() {

  }

  public void onWikiServiceCrash() {

  }

  public void onWikiServiceRecovery() {

  }

  public void onStartOfMotionPlay(String s) {

  }

  public void onPauseOfMotionPlay(String s) {

  }

  public void onStopOfMotionPlay(String s) {

  }

  @SimpleEvent(description = "motion complete event")
  public void onCompleteOfMotionPlay(String s) {
    mMotion_complete = true;

    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "onCompleteOfMotionPlay", s);
      }
    });    
  }

  public void onPlayBackOfMotionPlay(String s) {

  }

  public void onErrorOfMotionPlay(int i) {

  }

  public void onPrepareMotion(boolean b, String s, float v) {

  }

  public void onCameraOfMotionPlay(String s) {

  }

  public void onGetCameraPose(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8, float v9, float v10, float v11) {

  }

  public void onTouchEvent(int i, int i1) {
    
  }

  @SimpleEvent(description = "PIR sensor event")
  public void onPIREvent(int i) {
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "onPIREvent", i);
      }
    });
  }

  @SimpleEvent(description = "tap event")
  public void onTap(int bodyPart) {
    // kebbiParts.tapped(i);
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "onTap", bodyPart);
      }
    });
  }

  public void onLongPress(int i) {

  }

  public void onWindowSurfaceReady() {

  }

  public void onWindowSurfaceDestroy() {

  }

  public void onTouchEyes(int i, int i1) {

  }

  public void onRawTouch(int i, int i1, int i2) {

  }

  public void onFaceSpeaker(float v) {

  }

  public void onActionEvent(int i, int i1) {

  }

  public void onDropSensorEvent(int i) {

  }

  public void onMotorErrorEvent(int i, int i1) {

  }

  //start VoiceEventListener
  public void onWakeup(boolean b, String s, float v) {

  }

  public void onTTSComplete(boolean b) {
    mTts_complete = true;
    TTSCompleted(tts_sentence);    
  }

  @SimpleEvent(description = "trigger this event when Text To Speech is finished.")
  public void TTSCompleted(String sentence){
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "TTSCompleted", sentence);
      }
    });
  }

  public void onSpeechRecognizeComplete(boolean b, ResultType resultType, String s) {

  }

  // @SimpleEvent()
  public void onSpeech2TextComplete(boolean b, String s) {
    Log.d("nSpeech2TextCompletedddd","wait both action complete");
    stt_result = VoiceResultJsonParser.parseVoiceResult(s);
    STTCompleted(b, s, stt_result);    
  }

  @SimpleEvent()
  public void STTCompleted(boolean b, String result, String sentence){
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "STTCompleted", b, result, sentence);
      }
    });
  }

  public void onMixUnderstandComplete(boolean b, ResultType resultType, String s) {

  }

  public void onSpeechState(ListenType listenType, SpeechState speechState) {

  }

  public void onSpeakState(SpeakType speakType, SpeakState speakState) {

  }

  public void onGrammarState(boolean b, String s) {

  }

  public void onListenVolumeChanged(ListenType listenType, int i) {

  }

  public void onHotwordChange(HotwordState hotwordState, HotwordType hotwordType, String s) {

  }

  @Override
  public void onDestroy() {
    newSingleThreadPool.shutdown();
    mRobot.release();
  }
}
