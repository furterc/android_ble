package com.tuiste.christo.ble_playground;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

class Utilities
{
    private final static String TAG = Utilities.class.getSimpleName();

    private static final boolean DEBUG_ENABLED = false;

    static String byteArrayToHex(byte[] a)
    {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("0x%02X, ", b));
        return sb.toString();
    }

    static int fromByteArray(byte[] bytes)
    {
        if (bytes.length == 1)
            return bytes[0] & 0xFF;

        if (bytes.length == 2)
            return (bytes[1] & 0xFF) << 8 | bytes[0] & 0xFF;

        if (bytes.length == 4)
            return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);

        return 0;
    }

    static String byteArrayToString(byte[] bytes)
    {
        String string = "";

        for (byte mByte : bytes)
        {
            if (mByte == 0x00)
                return string;
            string += String.format("%c", mByte);
        }

        return string;
    }

    static long tagToLong (byte[] tag)
    {
        long rfId = 0L;
        byte shift = (byte) (tag.length - 1);
        for (int i = 0; i < tag.length; i++)
        {
            rfId |= (((long) tag[i] & 0xFF) << ((shift - i) * 8));
        }
        return rfId;
    }
}
