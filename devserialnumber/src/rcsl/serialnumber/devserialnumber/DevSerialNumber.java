package rcsl.serialnumber.devserialnumber;

import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailList;
import com.google.appinventor.components.annotations.SimpleProperty;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.os.Handler;

public class DevSerialNumber extends AndroidNonvisibleComponent implements ActivityCompat.OnRequestPermissionsResultCallback {

  private Context context;
  public final static int REQUEST_READ_PHONE_STATE = 1;
  public String aaa="no trigger";
  protected final Handler androidUIHandler = new Handler();

  public DevSerialNumber(ComponentContainer container) {
    super(container.$form());
    context = container.$form();

    int permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE);

    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
        androidUIHandler.post(new Runnable() {
          @Override
          public void run() {
            ActivityCompat.requestPermissions((Activity)context, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
            
            }
        });
    } else {
    }
  }

  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
      switch (requestCode) {
          case REQUEST_READ_PHONE_STATE:
              if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
              }
              break;
          default:
              break;
      }
  }
  
  @RequiresApi(api = Build.VERSION_CODES.O)
  @SimpleProperty(description = "Gets the hardware serial number")
  public String SerialNumber() {
    String serial_num;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        serial_num = Build.getSerial();
    }else {
        serial_num = Build.SERIAL;
    }
    return serial_num;
  }
}
