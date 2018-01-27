package com.tuiste.christo.ble_playground;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;


public class TerminalFragment extends Fragment
{
    private Context mContext;

    private BLE_characteristic mCharacteristic;
    private ListView mTerminalListView;
    private ArrayList<String> mTerminalArrayList;
    private ArrayAdapter<String> mTerminalArrayAdapter;

    private final Handler terminalHandler = new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(Message message)
        {
            String dataIn = "Device  : " + Utilities.byteArrayToString((byte[])message.obj);
            mTerminalArrayList.add(dataIn);
            mTerminalArrayAdapter.notifyDataSetChanged();
            scrollListViewToBottom();
            return false;
        }
    });

    public TerminalFragment()
    {
        // Required empty public constructor
    }

    public static TerminalFragment newInstance(BLE_characteristic characteristic)
    {
        TerminalFragment fragment = new TerminalFragment();
        fragment.setCharacteristic(characteristic);
        return fragment;
    }

    void setCharacteristic(BLE_characteristic characteristic)
    {
        mCharacteristic = characteristic;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mCharacteristic.registerHandler(terminalHandler);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);
        final View view =inflater.inflate(R.layout.fragment_terminal, container, false);
        mContext = view.getContext();

        mTerminalListView = view.findViewById(R.id.terminal_listView);
        mTerminalArrayList = new ArrayList<>();
        mTerminalArrayAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1, mTerminalArrayList);
        mTerminalListView.setAdapter(mTerminalArrayAdapter);


        final EditText editText = view.findViewById(R.id.terminal_editText);

        final Button buttonSend = view.findViewById(R.id.terminal_button);
        buttonSend.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                String data = editText.getText().toString();
                if(data.equals(""))
                {
                    editText.setError("BLE gaan breek.");
                    return;
                }
                editText.setText("");

                mTerminalArrayList.add("Android : " + data);
                mTerminalArrayAdapter.notifyDataSetChanged();
                scrollListViewToBottom();

                byte[] bytes = data.getBytes();
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
        inflater.inflate(R.menu.terminal_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if(item.getItemId() == R.id.menu_term_clean)
        {
            mTerminalArrayList.clear();
            mTerminalArrayAdapter.notifyDataSetChanged();
        }

        return super.onOptionsItemSelected(item);
    }

    private void scrollListViewToBottom()
    {
        mTerminalListView.post(new Runnable()
        {
            @Override
            public void run()
            {
                mTerminalListView.setSelection(mTerminalArrayAdapter.getCount() - 1);
            }
        });
    }


}
