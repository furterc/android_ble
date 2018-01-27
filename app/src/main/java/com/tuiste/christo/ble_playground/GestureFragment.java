package com.tuiste.christo.ble_playground;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;


public class GestureFragment extends Fragment
{
    private static final String ARG_PARAM1 = "param1";
    private BLE_characteristic mCharacteristic;

    public GestureFragment()
    {
        // Required empty public constructor
    }

    public static GestureFragment newInstance(BLE_characteristic sampleCharacteristic)
    {
        GestureFragment fragment = new GestureFragment();
        fragment.setCharacteristic(sampleCharacteristic);

        return fragment;
    }

    private final Handler sampleCharHandler = new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(Message message)
        {
            Log.e("sampleHandler", "data: " + Utilities.byteArrayToString((byte[]) message.obj));
            return false;
        }
    });

    public void setCharacteristic(BLE_characteristic characteristic)
    {
        mCharacteristic = characteristic;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.e("Gesture", "onCreate");
        super.onCreate(savedInstanceState);
        mCharacteristic.registerHandler(sampleCharHandler);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_gesture, container, false);

        view.findViewById(R.id.buttonRegs).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                byte[] bytes = "regs".getBytes();
                mCharacteristic.write(bytes);
            }
        });

        view.findViewById(R.id.buttonLinks).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                byte[] bytes = "links".getBytes();
                mCharacteristic.write(bytes);
            }
        });

        view.findViewById(R.id.buttonOp).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                byte[] bytes = "op".getBytes();
                mCharacteristic.write(bytes);
            }
        });

        view.findViewById(R.id.buttonAf).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                byte[] bytes = "af".getBytes();
                mCharacteristic.write(bytes);
            }
        });

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        menu.clear();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return super.onOptionsItemSelected(item);
    }


}
