package com.tuiste.christo.ble_playground;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
{
    private final static String TAG = MainActivity.class.getSimpleName();
    public final static String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public final static String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    //UI Handler defines
    static final int UI_HANDLER_CONNECTION = 0;

    private Context mContext;
    private ProgressDialog progressDialogConnect;

    private String mDeviceName;
    private String mDeviceAddress;

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mNavigationEntries;

    ArrayList<MenuEntry> mMenuEntries = new ArrayList<>();

    private BLE_characteristic gestureChar = new BLE_characteristic(GattAttributes.UUID_SAMPLE_CHARACTERIC);
    private final BLE_CharacteristicList mCharacteristicList = new BLE_CharacteristicList();

    public static FragmentManager mFragmentManager;

    private final Handler mUIHandler = new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(Message message)
        {
            switch (message.arg1)
            {

                case UI_HANDLER_CONNECTION:
                {
                    if (message.arg2 == Communication.UI_HANDLER_ARG2_CONNECTION)
                    {
                        final String connectStatus = (String) message.obj;

                        if ("servicesDiscovered".equals(connectStatus))
                        {
                            onServicesConnected();
                        } else if ("disconnect".equals(connectStatus))
                        {
                            finish();
                        } else if ("notificationsSet".equals(connectStatus))
                        {
                            onNotificationsSet();
                        } else if ("characteristicFault".equals(connectStatus))
                        {
                            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                            builder.setTitle(R.string.alertDialog_CharFault_Title);
                            builder.setMessage(R.string.alertDialog_CharFault_Message);

                            builder.setPositiveButton("OK", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i)
                                {
                                    dialogInterface.dismiss();
                                    finish();
                                }
                            });

                            AlertDialog alert = builder.create();
                            alert.show();
                        }
                    }
                }
                break;
            }

            return false;
        }
    });


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mTitle = mDrawerTitle = getTitle();

        /* Add fragments to the navigation bar*/
        mMenuEntries.add(new MenuEntry("Gestures", GestureFragment.newInstance(gestureChar)));
        mMenuEntries.add(new MenuEntry("Terminal", TerminalFragment.newInstance(gestureChar)));

        /* Set navigation Bar entries */
        mNavigationEntries = new String[mMenuEntries.size()];
        for (int idx = 0; idx < mMenuEntries.size(); idx++)
            mNavigationEntries[idx] = mMenuEntries.get(idx).getDrawerName();

        /* Setup the left side drawer */
        setupDrawer(savedInstanceState, mNavigationEntries);

        /* Show connecting dialog */
        progressDialogConnect = new ProgressDialog(this);
        progressDialogConnect.setTitle("Connecting...");
        progressDialogConnect.setMessage("Please wait.");
        progressDialogConnect.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialogConnect.setIndeterminate(true);
        progressDialogConnect.setCancelable(false);
        progressDialogConnect.show();

        // get extras from intent
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        /* Try to connect*/
        Communication.getInstance().setup(this, mDeviceAddress);
        Communication.getInstance().registerUIHandler(mUIHandler, UI_HANDLER_CONNECTION);

        Log.e(TAG, "Trying to connect\nDevice Name:" + mDeviceName + "\nDevice Address:" + mDeviceAddress);

        /* Set up Characteristics */
        gestureChar.registerCharacteristic();
        gestureChar.setHdlcRequired(false);

        /* Add all characteristics to little checklist */
        mCharacteristicList.add(gestureChar);
        Communication.getInstance().setCharacteristicList(mCharacteristicList);
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState)
    {
        super.onPostCreate(savedInstanceState, persistentState);
        mDrawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (mDrawerToggle.onOptionsItemSelected(item))
            return true;

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        menu.clear();

        return super.onCreateOptionsMenu(menu);
    }

    private void onServicesConnected()
    {
        /* Set notifications here*/
        gestureChar.setNotification(true);
    }

    private void onNotificationsSet()
    {
        /* You are now operational */
        if (progressDialogConnect != null && progressDialogConnect.isShowing())
            progressDialogConnect.dismiss();
    }

    @Override
    protected void onDestroy()
    {
        if (progressDialogConnect != null && progressDialogConnect.isShowing())
            progressDialogConnect.dismiss();
        Communication.getInstance().destroy(mContext);
        super.onDestroy();
    }

    void setupDrawer(Bundle b, String[] navigationEntries)
    {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerList = findViewById(R.id.left_drawer);

        /* Set custom shadow to that overlays the main content when the drawer opens */
//        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        /* Set p the drawer's list view with items and on click Listener */
        mDrawerList.setAdapter(new ArrayAdapter<>(this, R.layout.drawer_list_item, navigationEntries));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        /* Enable ActionBar application icon to behave as action to toggle nav drawer */
        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                               /* host activity */
                mDrawerLayout,                      /* DrawerLayout object */
                null,                               /* nav drawer image to replace the 'Up' caret */
                R.string.navigation_drawer_open,    /* "open drawer" description for accessibility */
                R.string.navigation_drawer_close    /* "close drawer" description for accessibility */
        )
        {
            @Override
            public void onDrawerClosed(View drawerView)
            {
                getSupportActionBar().setTitle(mTitle);
                supportInvalidateOptionsMenu();
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView)
            {
                getSupportActionBar().setTitle(mDrawerTitle);
                supportInvalidateOptionsMenu();
            }
        };
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerToggle.syncState();

        if (b == null)
            selectItem(0);
    }

    private class DrawerItemClickListener implements AdapterView.OnItemClickListener
    {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l)
        {
            selectItem(i);
        }
    }

    private void selectItem(int position)
    {
        /* Update the main content by replacing fragments */
        Fragment fragment;
        fragment = mMenuEntries.get(position).getFragment();

        FragmentManager fragmentManager = getSupportFragmentManager();
        mFragmentManager = fragmentManager;
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        /* Update the selected item and title, then close the drawer */
        mDrawerList.setItemChecked(position, true);
        setTitle(mNavigationEntries[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    @Override
    public void setTitle(CharSequence title)
    {
        mTitle = title;

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(mTitle);

        super.setTitle(title);
    }

    private class MenuEntry
    {
        private String drawerName;
        private Fragment fragment;

        MenuEntry(String name, Fragment fragment)
        {
            this.drawerName = name;
            this.fragment = fragment;
        }

        Fragment getFragment()
        {
            return fragment;
        }

        String getDrawerName()
        {
            return drawerName;
        }
    }
}
