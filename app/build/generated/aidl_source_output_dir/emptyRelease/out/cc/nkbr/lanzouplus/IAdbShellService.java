/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: D:\DevTools\android-sdk\build-tools\35.0.0\aidl.exe -pD:\DevTools\android-sdk\platforms\android-36\framework.aidl -oD:\heiyao\src\app\build\generated\aidl_source_output_dir\emptyRelease\out -ID:\heiyao\src\app\src\main\aidl -ID:\heiyao\src\app\src\empty\aidl -ID:\heiyao\src\app\src\release\aidl -ID:\heiyao\src\app\src\emptyRelease\aidl -dC:\Users\Admin\AppData\Local\Temp\aidl10158318284631853546.d D:\heiyao\src\app\src\main\aidl\cc\nkbr\lanzouplus\IAdbShellService.aidl
 */
package cc.nkbr.lanzouplus;
public interface IAdbShellService extends android.os.IInterface
{
  /** Default implementation for IAdbShellService. */
  public static class Default implements cc.nkbr.lanzouplus.IAdbShellService
  {
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    @Override public java.lang.String installApk(android.os.ParcelFileDescriptor source, long size) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements cc.nkbr.lanzouplus.IAdbShellService
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an cc.nkbr.lanzouplus.IAdbShellService interface,
     * generating a proxy if needed.
     */
    public static cc.nkbr.lanzouplus.IAdbShellService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof cc.nkbr.lanzouplus.IAdbShellService))) {
        return ((cc.nkbr.lanzouplus.IAdbShellService)iin);
      }
      return new cc.nkbr.lanzouplus.IAdbShellService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_installApk:
        {
          android.os.ParcelFileDescriptor _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.os.ParcelFileDescriptor.CREATOR);
          long _arg1;
          _arg1 = data.readLong();
          java.lang.String _result = this.installApk(_arg0, _arg1);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements cc.nkbr.lanzouplus.IAdbShellService
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
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.lang.String installApk(android.os.ParcelFileDescriptor source, long size) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, source, 0);
          _data.writeLong(size);
          boolean _status = mRemote.transact(Stub.TRANSACTION_installApk, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777114);
    static final int TRANSACTION_installApk = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "cc.nkbr.lanzouplus.IAdbShellService";
  public void destroy() throws android.os.RemoteException;
  public java.lang.String installApk(android.os.ParcelFileDescriptor source, long size) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
