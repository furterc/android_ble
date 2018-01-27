package com.tuiste.christo.ble_playground;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;


public class BluetoothLeService extends Service
{
    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private final static boolean DEBUG_ENABLED = false;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;


    // various states
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.kses.glamble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.kses.glamble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.kses.glamble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.kses.glamble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.kses.glamble.EXTRA_DATA";
    public final static String EXTRA_CHARACTERISTIC_UUID = "com.kses.glamble.EXTRA_CHARACTERISTIC_UUID";

    // various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadCastUpdate(intentAction);

                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery: " + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server");
                broadCastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadCastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else
            {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadCastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else
            {
                Log.w(TAG, "onCharacteristicRead error: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            broadCastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            final byte[] data = characteristic.getValue();

            if (DEBUG_ENABLED)
            {
                if (data != null && data.length > 0)
                {
                    //show the status of the characteristic write
                    Log.i(TAG, "status= " + status + " of " + String.format("0x%02X", status) +
                            "\n value: " + Utilities.byteArrayToHex(data) + "\n len: " +
                            characteristic.getValue().length);
                }
            }
        }
    };


    private void broadCastUpdate(final String action)
    {
        final Intent intent = new Intent(action);

        sendBroadcast(intent);
    }

    private void broadCastUpdate(final String action, BluetoothGattCharacteristic characteristic)
    {
        final Intent intent = new Intent(action);

        // For all other profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0)
        {
            intent.putExtra(EXTRA_DATA, data);
            intent.putExtra(EXTRA_CHARACTERISTIC_UUID, characteristic.getUuid().toString());
        }
        sendBroadcast(intent);
    }

    class LocalBinder extends Binder
    {
        BluetoothLeService getService()
        {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        // after device is used call close to clean u resources properly.
        close();
        if (mBluetoothGatt != null)
            mBluetoothGatt.close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize()
    {
        // For API 18 and higher, get BTadapter through BTmanager.
        if (mBluetoothManager == null)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null)
            {
                Log.e(TAG, "unable to initiate BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result is
     * reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)}
     * callback
     */
    public boolean connect(final String address)
    {
        if (mBluetoothAdapter == null || address == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // previously connected device, try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null)
        {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect())
            {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else
                return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null)
        {
            Log.w(TAG, "Device not found. Unable to connect.");
            return false;
        }

        // We want to directly connect to the device, so we are setting the autoConnect parameter to false
        Log.w(TAG, "SDK VER: " + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            Log.w(TAG, "SDK >= 23");
            mBluetoothGatt = device.connectGatt(getBaseContext(), false, mGattCallback, TRANSPORT_LE);
        }
        else
        {
            Log.w(TAG, "SDK < 23");
            mBluetoothGatt = device.connectGatt(getBaseContext(), false, mGattCallback);
        }

        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)}}
     * callback
     */
    public void disconnect()
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized.");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly
     */
    public void close()
    {
        if (mBluetoothGatt == null)
            return;

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(BluetoothGatt, BluetoothGattCharacteristic, int)}
     * callback
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Write to a given {@code BluetoothGattCharacteristic}. The write is reported asynchronously
     * through the {@code BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)}
     * callback
     *
     * @param characteristic The characteristic to write to.
     */
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        if (DEBUG_ENABLED)
            Log.e(TAG, "Write type: " + String.format("0x%02X", characteristic.getWriteType()));

        // set write type to WRITE_TYPE_NO_RESPONSE if not set to it
        if (characteristic.getWriteType() != BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        boolean bWrite = mBluetoothGatt.writeCharacteristic(characteristic);

        if (DEBUG_ENABLED)
        {
            if (bWrite)
                Log.w(TAG, "Write = " + bWrite);
            else
                Log.e(TAG, "Write = " + bWrite);
        }

        return bWrite;
    }


    /**
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on
     * @param enabled        If true enable notification, False disables notification
     */
    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        if (DEBUG_ENABLED)
            Log.i(TAG, "notification set for characteristic: " + characteristic.getUuid().toString());

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(GattAttributes.UUID_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return mBluetoothGatt.writeDescriptor(descriptor);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be invoked
     * only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported devices
     */
    public List<BluetoothGattService> getSupportedGattServices()
    {
        if (mBluetoothGatt == null)
            return null;

        return mBluetoothGatt.getServices();
    }
}
