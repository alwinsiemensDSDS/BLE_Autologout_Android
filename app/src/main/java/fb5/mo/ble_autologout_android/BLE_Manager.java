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
import no.nordicsemi.android.ble.callback.FailCallback;

// One BLE Manager manages one connection to a GATT Server on a Windows Device

// TODO: How to get reliably every connection-loss within this manager?
// Todo: Waiting for disconnecting! (you abort it with making it immediatly null and not waiting for it..)
public class BLE_Manager extends BleManager {

    // Hard Coded Specific UUIDS
    final static String DISTANCE_SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";
    final static String DISTANCE_CHARACTERISTIC_UUID = "12345678-6d8d-4d8c-ab6d-bde67ea69da3";
    boolean invalidated = false;
    boolean isStopping = false;

    private BluetoothGattCharacteristic distanceMeasureDataPoint;

    public BLE_Manager(@NonNull Context context) {
        super(context);
    }

    @Override
    protected boolean shouldClearCacheWhenDisconnected() {
        return true;
    }

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

    // is called every time, when disconnecting
    //
    // 1. When Windows Bluetooth Server shutdowns (after locking)
    // 2. When Distance between Phone and Windows System gets too big
    @Override
    protected void onServicesInvalidated() {
        distanceMeasureDataPoint = null;

        // Scan-Mode will ensure that nobody is connected
        if (!invalidated && !isStopping){
            Intent scanModeIntent = new Intent(getContext(), BLE_ForegroundService.class);
            scanModeIntent.setAction(BLE_ForegroundService.Actions.StartScanning.toString());
            ContextCompat.startForegroundService(getContext(), scanModeIntent);
        }
        invalidated = true;

    }

    // ==== Public API ====

    // Recursive periodical RSSI Data Sending to Windows System
    public void writeRSSIStrength(Context context){
        if (distanceMeasureDataPoint != null){
            readRssi()
                    .with( (device, rssi1) ->
                            {
                                writeCharacteristic(distanceMeasureDataPoint, new byte[]{(byte)(rssi1*-1)}, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                        .done( d2 -> {
                                            Intent broadcastIntent = new Intent("RemoteRSSIChanged");
                                            broadcastIntent.putExtra("RemoteRSSI", rssi1);
                                            context.sendBroadcast(broadcastIntent);

                                        })
                                        .enqueue();
                                sleep(200).done(
                                        (s) ->{
                                            if(distanceMeasureDataPoint != null){
                                                writeRSSIStrength(context);
                                            }
                                        }).enqueue();
                            }
                    )
                    .enqueue();
        }

    }
}