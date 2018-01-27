package com.tuiste.christo.ble_playground;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

import static android.content.Context.BIND_AUTO_CREATE;

class Communication
{
    // singleton setup
    private static final Communication instance = new Communication();

    static Communication getInstance()
    {
        return instance;
    }

    private final static String TAG = Communication.class.getSimpleName();
    private static boolean DEBUG_ENABLED = false;

    final static int UI_HANDLER_ARG2_CONNECTION = 0;
    final static int UI_HANDLER_ARG2_BLUETOOTH_ARROW = 1;

    private final Object mNotificationObject = new Object();
    private List<BluetoothGattCharacteristic> mNotificationList = new LinkedList<>();

    private final Object mDataInNotificationObject = new Object();
    private List<BLE_DataReceived_Class> mDataInQueue = new LinkedList<>();

    private Handler mUIHandler;
    private int mUIHandlerArg1;
    private BLE_CharacteristicList mCharacterisiticList;

    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private List<BLE_characteristic> mRegisteredCharacteristics = new LinkedList<>();


    private Communication()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (true)
                {
                    synchronized (mNotificationObject)
                    {
                        try
                        {
                            mNotificationObject.wait();
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    try
                    {
                        if (DEBUG_ENABLED)
                            Log.i(TAG, "Notifications Waiting");

                        //wait 200ms before registering characteristics
                        Thread.sleep(20);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    while (!mNotificationList.isEmpty())
                    {
                        final BluetoothGattCharacteristic mChar = mNotificationList.remove(0);

                        if (mChar != null)
                        {
                            int count = 100;
                            if (mBluetoothLeService == null)
                                break;
                            try
                            {
                                while (!mBluetoothLeService.setCharacteristicNotification(mChar, true) && --count > 1)
                                {
                                    if (DEBUG_ENABLED)
                                        Log.i(TAG, "Waiting for notification");
                                    try
                                    {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                }
                                if (count < 5)
                                    break;
                            } catch (NullPointerException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        try
                        {
                            Thread.sleep(20);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    // services is discovered
                    if (mUIHandler != null)
                    {
                        Message msg = new Message();
                        msg.obj = "notificationsSet";
                        msg.arg1 = mUIHandlerArg1;
                        msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                        mUIHandler.sendMessage(msg);
                    }
                }
            }
        }).start();

        //Data in Queue
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (true)
                {
                    synchronized (mDataInNotificationObject)
                    {
                        try
                        {
                            if (DEBUG_ENABLED)
                                Log.i(TAG, "Data in queue waiting");
                            mDataInNotificationObject.wait();
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    while (!mDataInQueue.isEmpty())
                    {
                        try
                        {
                            final BLE_DataReceived_Class dataIn = mDataInQueue.remove(0);
                            if (dataIn != null)
                            {
                                final String uuid = dataIn.getUUID();
                                final byte[] data = dataIn.getByteArray();
                                //doen die kak hier
                                for (BLE_characteristic mCharacteristic : mRegisteredCharacteristics)
                                {
                                    if (mCharacteristic.getUUID().equals(uuid))
                                    {
                                        mCharacteristic.setData(data);
                                    }
                                }
                            }
                        } catch (NullPointerException e)
                        {
                            e.printStackTrace();
                            break;
                        }
                    }
                }
            }
        }).start();
    }

    void registerCharacteristic(BLE_characteristic mChar)
    {
        mRegisteredCharacteristics.add(mChar);
    }

    void registerUIHandler(Handler handler, int arg1)
    {
        mUIHandler = handler;
        mUIHandlerArg1 = arg1;
    }

    void setCharacteristicList(BLE_CharacteristicList list)
    {
        mCharacterisiticList = list;
    }

    void setNotification(BluetoothGattCharacteristic characteristic, boolean enabled)
    {
        if (characteristic != null && enabled)
        {
            mNotificationList.add(characteristic);
            synchronized (mNotificationObject)
            {
                mNotificationObject.notify();
            }
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder)
        {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) iBinder).getService();

            if (DEBUG_ENABLED)
                Log.d(TAG, "onServiceConnected");

            if (!mBluetoothLeService.initialize())
            {
                Log.e(TAG, "Unable to initialize Bluetooth");
                if (mUIHandler != null)
                {
                    Message msg = new Message();
                    msg.obj = "disconnect";
                    msg.arg1 = mUIHandlerArg1;
                    msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                    mUIHandler.sendMessage(msg);
                }
            }

            // Automatically connect to the device upon successful start-up initialization.
            Log.e(TAG, "trying to connect on service connected!");
//            Log.e(TAG, "not /trying to connect on service connected anymore!");
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mBluetoothLeService = null;
            if (mUIHandler != null)
            {
                Message msg = new Message();
                msg.obj = "disconnect";
                msg.arg1 = mUIHandlerArg1;
                msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                mUIHandler.sendMessage(msg);
            }
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (action == null)
            {
                if (DEBUG_ENABLED)
                    Log.e(TAG, "onReceive action == null");

                return;
            }

            // switch between the difference actions
            switch (action)
            {
                // Connected
                case BluetoothLeService.ACTION_GATT_CONNECTED:
                {
                    if (DEBUG_ENABLED)
                        Log.i(TAG, "Connected");

                    if (mUIHandler != null)
                    {
                        Message msg = new Message();
                        msg.obj = "connect";
                        msg.arg1 = mUIHandlerArg1;
                        msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                        mUIHandler.sendMessage(msg);
                    }
                }
                break;

                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                {
                    if (DEBUG_ENABLED)
                        Log.i(TAG, "Disconnected");

                    if (mUIHandler != null)
                    {
                        Message msg = new Message();
                        msg.obj = "disconnect";
                        msg.arg1 = mUIHandlerArg1;
                        msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                        mUIHandler.sendMessage(msg);
                    }
                }
                break;

                case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
                {
                    List<BluetoothGattService> mServiceList = mBluetoothLeService.getSupportedGattServices();

                    // loop through the various services
                    for (BluetoothGattService mService : mServiceList)
                    {
                        if (DEBUG_ENABLED)
                            Log.i(TAG, "Service discovered\t\t\t - UUID: " + mService.getUuid().toString());

                        List<BluetoothGattCharacteristic> mCharacteristics = mService.getCharacteristics();

                        // loop through the various service's characteristics
                        for (BluetoothGattCharacteristic mCharacter : mCharacteristics)
                        {
                            if (DEBUG_ENABLED)
                                Log.i(TAG, "\t\tCharacteristic found - UUID: " + mCharacter.getUuid().toString());

                            for (BLE_characteristic mCharacteristic : mRegisteredCharacteristics)
                            {
                                if (mCharacteristic.getUUID().equals(mCharacter.getUuid().toString()))
                                {
                                    mCharacteristic.setCharacteristic(mCharacter);
                                }
                            }
                        }
                    }

                    // check that all the characteristics is registered
                    if (!mCharacterisiticList.isRegistered())
                    {
                        if (mUIHandler != null)
                        {
                            Message msg = new Message();
                            msg.obj = "characteristicFault";
                            msg.arg1 = mUIHandlerArg1;
                            msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                            mUIHandler.sendMessage(msg);
                        }
                        return;
                    }

                    // services is discovered
                    if (mUIHandler != null)
                    {
                        Message msg = new Message();
                        msg.obj = "servicesDiscovered";
                        msg.arg1 = mUIHandlerArg1;
                        msg.arg2 = UI_HANDLER_ARG2_CONNECTION;
                        mUIHandler.sendMessage(msg);
                    }
                }
                break;

                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                {


                    final String uuid = intent.getStringExtra(BluetoothLeService.EXTRA_CHARACTERISTIC_UUID);
                    final byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                    final BLE_DataReceived_Class dataClass = new BLE_DataReceived_Class(uuid, data);

                    try
                    {
                        mDataInQueue.add(dataClass);
                    } catch (NullPointerException e)
                    {
                        e.printStackTrace();
                    }

                    synchronized (mDataInNotificationObject)
                    {
                        mDataInNotificationObject.notify();
                    }
                }
                break;

                case BluetoothLeService.EXTRA_DATA:
                {
                    String mStr = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);

                    if (DEBUG_ENABLED)
                        Log.i(TAG, "Extra Data Received: " + mStr);
                }
            }
        }
    };

    void setup(Context context, String deviceAddress)
    {
        if (DEBUG_ENABLED)
            Log.w(TAG, "setup() called");

        mDeviceAddress = deviceAddress;
        Intent gattServiceIntent = new Intent(context, BluetoothLeService.class);
        context.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        context.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
//
//        if (mBluetoothLeService != null)
//        {
//            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
//            Log.d(TAG, "connect request result= " + result);
//        }
    }

//    void resume(Context context)
//    {
//        if (DEBUG_ENABLED)
//            Log.w(TAG, "resume called");
//
//
//    }

//    void pause(Context context)
//    {
//        if (DEBUG_ENABLED)
//            Log.w(TAG, "pause() called");
//
//
//    }

    void destroy(Context context)
    {
        Log.w(TAG, "destroy() called");
        context.unregisterReceiver(mGattUpdateReceiver);
        context.unbindService(mServiceConnection);

        // clear registered characteristics
        mRegisteredCharacteristics.clear();

        mBluetoothLeService.disconnect();
        mBluetoothLeService = null;
    }

    void readCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        try
        {
            mBluetoothLeService.readCharacteristic(characteristic);
        } catch (NullPointerException e)
        {
            Log.e(TAG, "readChar error: ", e);
        }
    }

    boolean writeCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        return mBluetoothLeService.writeCharacteristic(characteristic);
    }

    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
