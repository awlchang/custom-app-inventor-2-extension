/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
// package com.example.kebbifacetracking;
package rcsl.robotblock.kebbi2;
// Declare any non-default types here with import statements

public interface ISwitchTrack extends android.os.IInterface
{
  /** Default implementation for ISwitchTrack. */
  public static class Default implements ISwitchTrack
  {
    @Override public void enableTrack() throws android.os.RemoteException
    {
    }
    @Override public void disableTrack() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements ISwitchTrack
  {
    private static final java.lang.String DESCRIPTOR = "com.example.kebbifacetracking.ISwitchTrack";
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.example.kebbifacetracking.ISwitchTrack interface,
     * generating a proxy if needed.
     */
    public static ISwitchTrack asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof ISwitchTrack))) {
        return ((ISwitchTrack)iin);
      }
      return new ISwitchTrack.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
        case TRANSACTION_enableTrack:
        {
          data.enforceInterface(descriptor);
          this.enableTrack();
          reply.writeNoException();
          return true;
        }
        case TRANSACTION_disableTrack:
        {
          data.enforceInterface(descriptor);
          this.disableTrack();
          reply.writeNoException();
          return true;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements ISwitchTrack
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void enableTrack() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_enableTrack, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            getDefaultImpl().enableTrack();
            return;
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void disableTrack() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_disableTrack, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            getDefaultImpl().disableTrack();
            return;
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      public static ISwitchTrack sDefaultImpl;
    }
    static final int TRANSACTION_enableTrack = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_disableTrack = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    public static boolean setDefaultImpl(ISwitchTrack impl) {
      // Only one user of this interface can use this function
      // at a time. This is a heuristic to detect if two different
      // users in the same process use this function.
      if (Stub.Proxy.sDefaultImpl != null) {
        throw new IllegalStateException("setDefaultImpl() called twice");
      }
      if (impl != null) {
        Stub.Proxy.sDefaultImpl = impl;
        return true;
      }
      return false;
    }
    public static ISwitchTrack getDefaultImpl() {
      return Stub.Proxy.sDefaultImpl;
    }
  }
  public void enableTrack() throws android.os.RemoteException;
  public void disableTrack() throws android.os.RemoteException;
}
