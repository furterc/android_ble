package com.tuiste.christo.ble_playground;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.Serializable;
import java.util.Arrays;

class BLE_characteristic implements Serializable
{
    private static String TAG = BLE_characteristic.class.getSimpleName();
    private final static boolean DEBUG_ENABLED = false;

    private final Framer framer = new Framer();

    private boolean registered = false;
    private String mUUID;
    private Handler mHandler = null;
    private int mHandlerArg1 = 0;
    private BluetoothGattCharacteristic mCharacteristic;
    private byte[] mData;
    private boolean hdlcRequired = false;

    //debug
    private long currentTime;

    BLE_characteristic(String mUUID)
    {
        this.setUUID(mUUID);
    }

    boolean isRegistered()
    {
        return registered;
    }

    private void setUUID(String mUUID)
    {
        this.mUUID = mUUID;
    }

    String getUUID()
    {
        return mUUID;
    }

    void registerHandler(Handler handler)
    {
        this.mHandler = handler;
    }

    void registerHandler(Handler handler, int arg1)
    {
        this.mHandler = handler;
        this.mHandlerArg1 = arg1;
    }

    public void unregisterHandler()
    {
        this.mHandler = null;
    }

    void setCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        this.mCharacteristic = characteristic;
        this.registered = true;
    }

    public BluetoothGattCharacteristic getCharacteristic()
    {
        return mCharacteristic;
    }

    void registerCharacteristic()
    {
        Communication.getInstance().registerCharacteristic(this);
    }

    boolean setNotification(boolean enabled)
    {
        if (mCharacteristic == null)
        {
            Log.e(TAG, "setNotification failed, characteristic == null");
            return false;
        }

        if ((mCharacteristic.getProperties() & 0x10) != 0x10)
            return false;

        Communication.getInstance().setNotification(mCharacteristic, enabled);

        if (DEBUG_ENABLED)
            Log.i(TAG, "setNotification: " + mCharacteristic.getUuid().toString());

        return true;
    }

    public void setData(byte[] data)
    {
        if (!hdlcRequired)
        {
            this.mData = data;

            if (DEBUG_ENABLED)
            {
                Log.i(TAG, "noHDLC data ready - hex: " + Utilities.byteArrayToHex(mData));
                Log.i(TAG, "noHDLC data len: " + mData.length);
            }

            if (mHandler != null)
            {
                Message msg = new Message();
                msg.obj = mData;
                msg.arg1 = mHandlerArg1;
                mHandler.sendMessage(msg);
            }
        } else
        {
            if (framer.rxData(data))
            {
                //data ready
                this.mData = framer.getFrame();

                if (DEBUG_ENABLED)
                {
                    Log.i(TAG, "HDLC data ready - hex: " + Utilities.byteArrayToHex(mData));
                    Log.i(TAG, "HDLC data len: " + mData.length);
                }

                if (mHandler != null)
                {
                    Message msg = new Message();
                    msg.obj = mData;
                    msg.arg1 = mHandlerArg1;
                    mHandler.sendMessage(msg);
                }
            }
        }
    }

    public byte[] getData()
    {
        return mData;
    }

    void setHdlcRequired(boolean hdlcRequired)
    {
        this.hdlcRequired = hdlcRequired;
    }

    public boolean isHdlcRequired()
    {
        return hdlcRequired;
    }

    void write(byte[] data)
    {
        if (DEBUG_ENABLED)
            currentTime = System.currentTimeMillis();

        if (mCharacteristic != null)
        {
            mCharacteristic.setWriteType(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
            byte[] sBytes;
            if (hdlcRequired)
            {
                Framer framer = new Framer();
                sBytes = framer.frameCreate(data);
            } else
            {
                sBytes = data;
            }

            if (sBytes.length < 21)
            {
                sendPacket(sBytes);
                return;
            }

            int byteCount = sBytes.length;
            int chunkSize = 20;
            int packetsToSend = (int) Math.ceil((double) byteCount / (double) chunkSize);

            byte[][] packets = new byte[packetsToSend][chunkSize];

            int start = 0;
            for (int i = 0; i < packets.length; i++)
            {
                packets[i] = Arrays.copyOfRange(sBytes, start, start + chunkSize);
                start += chunkSize;
            }

            sendSplitPackets(packets);
        } else
            Log.e(TAG, "mCharacteristic == null");
    }

    private void sendSplitPackets(final byte[][] packets)
    {
        if (DEBUG_ENABLED)
            for (int cnt = 0; cnt < packets.length; cnt++)
            {
                Log.e(TAG, "Packet[" + cnt + "]: " + Utilities.byteArrayToHex(packets[cnt]));
            }

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for (byte[] packet : packets)
                {
                    while (!sendPacket(packet))
                    {
                        if (DEBUG_ENABLED)
                            Log.e(TAG, "Failed to send, trying again!");
                        try
                        {
                            Thread.sleep(1);
                        } catch (InterruptedException e)
                        {
                            Log.e(TAG, "thread sleep error", e);
                        }
                    }
                    try
                    {
                        Thread.sleep(1);
                    } catch (InterruptedException e)
                    {
                        Log.e(TAG, "thread sleep error", e);
                    }
                }

                if (DEBUG_ENABLED)
                {
                    long eTime = System.currentTimeMillis() - currentTime;
                    Log.e(TAG, "elapsed Time (millis): " + eTime + "\tsec: " + (double) eTime / (double) 1000);
                }
            }
        }).start();
    }

    private boolean sendPacket(byte[] packet)
    {
        if (DEBUG_ENABLED)
            Log.i(TAG, "sending packet: " + new String(packet));

        mCharacteristic.setValue(packet);
        return Communication.getInstance().writeCharacteristic(mCharacteristic);
    }

    void read()
    {
        if (this.mCharacteristic != null)
            Communication.getInstance().readCharacteristic(mCharacteristic);
        else
            Log.e(TAG, "mCharacteristic == null");
    }
}