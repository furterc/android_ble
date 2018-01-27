package com.tuiste.christo.ble_playground;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

class Framer implements Serializable
{
    private static String TAG = Framer.class.getSimpleName();
    private static boolean DEBUG_ENABLED = false;

    private static final int FLAG = 0x7E;
    private static final int ESC = 0x7D;
    private static final int XOR = 0x20;

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream in;

    private int out_crc;
    private boolean escaping;

    //Crc class
    private Crc crc = new Crc();

    Framer()
    {
        out = new ByteArrayOutputStream();
        in = new ByteArrayOutputStream();
        reset_framer();
    }

    byte[] getFrame()
    {

        byte[] b = in.toByteArray();
        reset_framer();
        return b;
    }

    boolean rxData(byte[] b)
    {
        if (b != null)
        {
            for (byte aB : b)
            {
                if (unframe(aB))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void startFrame()
    {
        out.reset();
        out.write(FLAG);
    }

    private void addFrame(byte data)
    {
        write_byte(data);
    }

    private void endFrame()
    {
        write_byte((byte) (out_crc & 0xFF));
        write_byte((byte) ((out_crc >> 8) & 0xFF));
        out.write(FLAG);
        if ((out.size() % 64) == 0)
        {
            out.write(0x00);
        }
    }

    byte[] frameCreate(byte[] data)
    {
        if (data != null)
        {
            startFrame();
            for (byte aData : data)
            {
                addFrame(aData);
            }

            out_crc = crc.ccitt_crc16(data, data.length);
            out_crc ^= 0xFFFF;

            endFrame();

            return out.toByteArray();
        }
        return null;
    }

    private void reset_framer()
    {
        in.reset();
        escaping = false;
    }

    private boolean unframe(byte b)
    {
        if (b == FLAG)
        {
            //System.out.println("Rx FLAG in.size = "+in.size());
            if (in.size() > 2)
            {

                byte[] temp = in.toByteArray();

                // Check CRC
                if (crc.ccitt_crc16(temp, temp.length) != Crc.GOOD_CRC)
                {
                    if (DEBUG_ENABLED)
                        Log.e(TAG, "WARNING - Bad CRC");

                    in.reset();
                    return false;
                }

                // Double check this?
                in.reset();
                in.write(temp, 0, temp.length - 2);

                return true;
            }
            reset_framer();
        } else if (b == ESC && !escaping)
        {
            escaping = true;
        } else
        {
            if (escaping)
            {
                if (DEBUG_ENABLED)
                    Log.i(TAG, String.format("Escaping b from 0x%02X ", b));

                b ^= XOR;

                if (DEBUG_ENABLED)
                    Log.i(TAG, String.format("to 0x%02X ", b));

                escaping = false;
            }
            in.write(b);
        }
        return false;
    }

    private void write_byte(byte data)
    {
        if ((data == FLAG) ||
                (data == ESC))
        {
            out.write(ESC);
            data ^= XOR;
        }
        out.write(data);
    }
}
