package rcsl.block.mdns;

// import com.google.appinventor.components.annotations.SimpleFunction;
// import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
// import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailList;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.*;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.runtime.util.*;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import android.content.Context;
import android.os.Handler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MdnsClient extends AndroidNonvisibleComponent implements NsdManager.DiscoveryListener {

  private Context context;
  private Handler mHandler;
  final Component thisComponent = this;

  private NsdManager mNsdManager;
  private NsdManager.ResolveListener mResolveListener;
  private NsdServiceInfo mServiceInfo;

  // The NSD service type that the RPi exposes.
  private String SERVICE_TYPE = "_workstation._tcp.";
  private String SERVICE_NAME = "toy";

  ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

  public MdnsClient(ComponentContainer container) {
    super(container.$form());
    context = container.$context();

    mHandler = new Handler();

    mNsdManager = (NsdManager)(context.getSystemService(context.NSD_SERVICE));    
  }

  @SimpleProperty(description = "The NSD service type that the devices expose.")
  public String ServiceType() {
      return SERVICE_TYPE;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "_workstation._tcp.")
  @SimpleProperty(description = "The NSD service type that the devices expose.")
  public void ServiceType(String value) {
      SERVICE_TYPE = value.trim();
  }

  @SimpleProperty(description = "The NSD service name that the devices expose.")
  public String ServiceName() {
      return SERVICE_NAME;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "toy")
  @SimpleProperty(description = "The NSD service name that the devices expose.")
  public void ServiceName(String value) {
      SERVICE_NAME = value.trim();
  }

  @SimpleFunction
  public void StartDeviceDiscovery(){
    mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this);
  }

  @SimpleFunction
  public void StopDeviceDiscovery(){
    mNsdManager.stopServiceDiscovery(this);
  }

  public void ResolveListener(){
    mResolveListener = new NsdManager.ResolveListener() {

        @Override
        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
            
        }

        @Override
        public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
            String deviceName = nsdServiceInfo.getServiceName();
            String hostName = nsdServiceInfo.getHost().getHostName();

            try {
                InetAddress inetAddress = InetAddress.getByName(hostName);
                String ipAddr = inetAddress.getHostAddress();

                ServiceResolved(deviceName, hostName, ipAddr);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            // cachedThreadPool.execute(new Runnable() {
            //     @Override
            //     public void run() {
            //         ServiceResolved(nsdServiceInfo.getHost().getHostName());
            //     }
            // });
        }
    };
  }

  @SimpleEvent()
  public void ServiceResolved(String deviceName, String hostName, String ipAddress){
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "ServiceResolved", deviceName, hostName, ipAddress);
      }
    });
  }

  @Override
  public void onStartDiscoveryFailed(String s, int i) {
      mNsdManager.stopServiceDiscovery(this);
  }

  @Override
  public void onStopDiscoveryFailed(String s, int i) {
      mNsdManager.stopServiceDiscovery(this);
  }

  @Override
  public void onDiscoveryStarted(String s) {

  }

  @Override
  public void onDiscoveryStopped(String s) {

  }

  public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
    String service_name = nsdServiceInfo.getServiceName();
    // String type = nsdServiceInfo.getServiceType();

    try {
        Thread.sleep(100);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }

    if (service_name.contains(SERVICE_NAME)) {
        service_name = service_name.split(" ")[0];
        ServiceFound(service_name);
    }

    ResolveListener();
    mNsdManager.resolveService(nsdServiceInfo, mResolveListener);
  }

  @SimpleEvent(description = "when found mdns service")
  public void ServiceFound(String serviceName){
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "ServiceFound", serviceName);
      }
    });
  }

  @Override
  public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
    String lostServiceName = nsdServiceInfo.getServiceName();
    
    try {
        Thread.sleep(100);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }

    lostServiceName = lostServiceName.split(" ")[0];
    ServiceLost(lostServiceName);
  }

  @SimpleEvent(description = "when lost mdns service")
  public void ServiceLost(String lostServiceName){
    mHandler.post(new Runnable() {
      public void run() {
        EventDispatcher.dispatchEvent(thisComponent, "ServiceLost", lostServiceName);
      }
    });
  }
}
