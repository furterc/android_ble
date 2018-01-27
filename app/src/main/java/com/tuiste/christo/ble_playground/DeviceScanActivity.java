package com.tuiste.christo.ble_playground;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class DeviceScanActivity extends ListActivity
{
    private static String TAG = DeviceScanActivity.class.getSimpleName();

    /* Auto connect */
    private boolean mAutoConnect = false;
    String mBTAddress = null;
    public static final String PREFS_NAME = "MyPrefsFile";

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private LeDeviceListAdapter mLeDeviceListAdapter;

    // scan period in ms
    private static final long SCAN_PERIOD = 10000;

    // request code for bt intent
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

//    private static final String ksesMAC = "34:29:8F:D";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Prompt for permissions
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Log.w("BleActivity", "Location access not granted!");
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            }
        }


        if (getActionBar() != null)
            getActionBar().setTitle(R.string.deviceScanActivity_title);

        mHandler = new Handler();

        // check if device support ble
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Toast.makeText(this, R.string.deviceScanActivity_bleNotSupported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // init Bluetooth adapter.
        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // ensure BT is available
        if (mBluetoothAdapter == null)
        {
            Toast.makeText(this, R.string.deviceScanActivity_btNotSupported, Toast.LENGTH_SHORT).show();
            finish();
        }

    }

    @Override
    protected void onResume()
    {
        Log.i(TAG, "onResume() called");
        super.onResume();

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        mAutoConnect = settings.getBoolean("autoConnect", false);
        mBTAddress = settings.getString("btAddress", "0");

        // ensure BT is enabled
        if (!mBluetoothAdapter.isEnabled())
        {
            Intent enableBTinIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTinIntent, REQUEST_ENABLE_BT);
        }

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        invalidateOptionsMenu();
        scanLeDevice(true);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
//        Toast.makeText(this, "Device clicked\n" + device.getName() + "\n" + device.getAddress(),Toast.LENGTH_SHORT).show();

        if (device == null)
            return;

        if (mScanning)
        {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }

        mBTAddress = device.getAddress();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("btAddress", mBTAddress);
        editor.apply();
        connect(device);
    }

    private void connect(BluetoothDevice device)
    {
        final Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(MainActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        startActivity(intent);
    }

    private Drawable mAutoIcon;

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);

        mAutoIcon = menu.findItem(R.id.menu_autoConnect).getIcon();

        if (mAutoConnect)
        {
            if ("0".equals(mBTAddress))
                mAutoIcon.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP);
            else
                mAutoIcon.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_ATOP);
        } else
            mAutoIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        if (!mScanning)
        {
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else
        {
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_refresh).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case R.id.menu_autoConnect:
                mAutoConnect = !mAutoConnect;

                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("autoConnect", mAutoConnect);
                editor.apply();

                if (mAutoConnect)
                {
                    if ("0".equals(mBTAddress))
                        mAutoIcon.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP);
                    else
                        mAutoIcon.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_ATOP);

                    Toast.makeText(getApplicationContext(), "AutoConnect selected for next device.", Toast.LENGTH_SHORT).show();
                } else
                {
                    Toast.makeText(getApplicationContext(), "AutoConnect off.", Toast.LENGTH_SHORT).show();
                    mAutoIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                    mBTAddress = "0";
                    editor.putString("btAddress", mBTAddress);
                    editor.apply();
                }

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED)
        {
            Toast.makeText(this, R.string.deviceScanActivity_btRequired, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_LOCATION_PERMISSION)
        {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, R.string.deviceScanActivity_permissionRequired, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
    }

    private void scanLeDevice(final boolean enable)
    {
        if (enable)
        {
            // stop scanning after period elapsed
            mHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else
        {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // ble scan callback
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback()
    {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, final int rssi, byte[] bytes)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {

                    if (mAutoConnect && bluetoothDevice.getAddress().equals(mBTAddress))
                        connect(bluetoothDevice);
                    //                    if(bluetoothDevice.getAddress() != null && bluetoothDevice.getAddress().contains(ksesMAC))
//                    {
                    mLeDeviceListAdapter.addDevice(bluetoothDevice);
//                    }


//                    mLeDeviceListAdapter.notifyDataSetChanged();
//                    Toast.makeText(getApplicationContext(), "rssi: " + rssi, Toast.LENGTH_SHORT).show();
                }
            });
        }
    };


    private class LeDeviceListAdapter extends BaseAdapter
    {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflater;

        LeDeviceListAdapter()
        {
            super();
            mLeDevices = new ArrayList<>();
            mInflater = DeviceScanActivity.this.getLayoutInflater();
        }

        void addDevice(BluetoothDevice device)
        {
            if (!mLeDevices.contains(device))
                mLeDevices.add(device);

            notifyDataSetChanged();
        }

        BluetoothDevice getDevice(int position)
        {
            return mLeDevices.get(position);
        }

        void clear()
        {
            mLeDevices.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount()
        {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i)
        {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i)
        {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup)
        {
            ViewHolder viewHolder;

            if (view == null)
            {
                view = mInflater.inflate(R.layout.listitem_ble_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.textView_deviceName);
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.textView_deviceAddress);
                view.setTag(viewHolder);
            } else
            {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();

            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(getResources().getString(R.string.deviceScanActivity_unknownDevice));

            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    private static class ViewHolder
    {
        TextView deviceName;
        TextView deviceAddress;
    }

}
