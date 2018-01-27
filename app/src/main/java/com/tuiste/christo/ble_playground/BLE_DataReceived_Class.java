package com.tuiste.christo.ble_playground;

/**
 * Created by cfurter on 2017/06/05.
 */

public class BLE_DataReceived_Class
{
    private String mUUID;
    private byte[] mData;

    public BLE_DataReceived_Class(String uuid, byte[] data)
    {
        mUUID = uuid;
        mData = data;
    }

    public void setUUID(String uuid)
    {
        this.mUUID = uuid;
    }

    public String getUUID()
    {
        return mUUID;
    }

    public void setByteArray(byte[] data)
    {
        this.mData = data;
    }

    public byte[] getByteArray()
    {
        return mData;
    }
}
