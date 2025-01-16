package fb5.mo.ble_autologout_android;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.ReadRssiRequest;
import no.nordicsemi.android.ble.callback.FailCallback;

public class BLE_Manager extends BleManager {
    final String DISTANCE_SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";
    final String DISTANCE_CHARACTERISTIC_UUID = "12345678-6d8d-4d8c-ab6d-bde67ea69da3";


    public BLE_Manager(@NonNull Context context) {
        super(context);

    }

    private BluetoothGattCharacteristic distanceMeasureDataPoint;

    @Override
    protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {
        BluetoothGattService distanceService = gatt.getService(UUID.fromString(DISTANCE_SERVICE_UUID));

        if(distanceService != null){
            distanceMeasureDataPoint = distanceService.getCharacteristic(UUID.fromString(DISTANCE_CHARACTERISTIC_UUID));
        }
        return distanceMeasureDataPoint != null;
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    protected void onServicesInvalidated() {
        disconnect();
        distanceMeasureDataPoint = null;
        Intent broadcastIntent = new Intent("ConnectionStatusChanged");
        broadcastIntent.putExtra("ConnectionStatus", "disconnected");
        getContext().sendBroadcast(broadcastIntent);
        Intent stopIntent = new Intent(getContext(), BLE_ForegroundService.class);
        stopIntent.setAction(BLE_ForegroundService.Actions.StartLowPowerScanningMode.toString());
        ContextCompat.startForegroundService(getContext(), stopIntent);
    }

    // ==== Public API ====

    public void writeRSSIStrength(Context context){

        try {
            if(!MainActivity.isServiceRunningInForeground(context, BLE_ForegroundService.class)){
                onServicesInvalidated();
                return;
            }
            readRssi()
                    .with( (device, rssi1) ->
                            {
                                writeCharacteristic(distanceMeasureDataPoint, new byte[]{(byte)(rssi1*-1)}, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)

                                        .done( d2 -> {
                                            Intent broadcastIntent = new Intent("RemoteRSSIChanged");
                                            broadcastIntent.putExtra("RemoteRSSI", rssi1);
                                            context.sendBroadcast(broadcastIntent);
                                            writeRSSIStrength(context);
                                        })
                                        .enqueue();
                            }
                    )
                    .enqueue();
        } catch (Exception e) {
            Log.e("ERROR BLE", "Write Error");
        }
    }
}