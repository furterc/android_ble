package com.tuiste.christo.ble_playground;

import java.util.LinkedList;

class BLE_CharacteristicList
{
    private LinkedList<BLE_characteristic> mCharacteristics = new LinkedList<>();

    void add(BLE_characteristic characteristic)
    {
        mCharacteristics.add(characteristic);
    }

    void add(BLE_characteristic... characteristics)
    {
        for (BLE_characteristic characteristic : characteristics)
            mCharacteristics.add(characteristic);
    }

    boolean isRegistered()
    {
        for (BLE_characteristic characteristic : mCharacteristics)
            if (!characteristic.isRegistered())
                return false;

        return true;
    }
}
