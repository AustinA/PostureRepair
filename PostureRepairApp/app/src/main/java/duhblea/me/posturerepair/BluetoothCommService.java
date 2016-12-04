package duhblea.me.posturerepair;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class BluetoothCommService extends IntentService {
    public static volatile boolean shouldContinue = true;

    private static final UUID COMMS_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final String BT_MAC_ADDRESS = "00:06:66:69:74:10";

    private BluetoothDevice btDevice = null;
    private BluetoothSocket commSocket = null;
    private InputStream commInputStream = null;

    // Main user interface looper for side thread posting to UI
    private Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    public static volatile boolean isRunning = false;

    // Data storage and parsing
    Gson gson = new Gson();


    public BluetoothCommService() {
        super("BluetoothCommService");

    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void connectBluetooth(Context context) {
        Intent intent = new Intent(context, BluetoothCommService.class);
        intent.setAction(CommsConstants.ACTION_CONNECT_BT);

        context.startService(intent);
    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void disconnectBluetooth(Context context) {
        Intent intent = new Intent(context, BluetoothCommService.class);
        intent.setAction(CommsConstants.ACTION_DISCONNECT_BT);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (CommsConstants.ACTION_CONNECT_BT.equals(action)) {

                initializeBluetooth();

            } else if (CommsConstants.ACTION_DISCONNECT_BT.equals(action)) {

                disconnectBluetooth();
            }
        }
    }


    /**
     * Initialize Bluetooth
     */
    private void initializeBluetooth() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter != null) {

                if (btAdapter.getBondedDevices() != null && !btAdapter.getBondedDevices().isEmpty()) {
                    Object[] connectedDevices = btAdapter.getBondedDevices().toArray();

                    for (int i = 0; i < connectedDevices.length; i++) {
                        BluetoothDevice device = (BluetoothDevice) connectedDevices[i];

                        if (device.getAddress().equals(BT_MAC_ADDRESS)) {
                            btDevice = device;

                            if (!isRunning) {
                                commsLoop();
                            }
                            else {

                                uiThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "Communication already initialized",
                                                Toast.LENGTH_LONG).show();
                                    }
                                });

                            }

                            break;
                        }

                    }


                } else {
                    uiThreadHandler.post(new Runnable(){

                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Posture Repair sensor not found", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
    }

    /**
     * Disconnect a Posture Repair Sensor
     */
    private void disconnectBluetooth() {
        if (btDevice != null) {
            if (isRunning) {
                isRunning = false;

                btDevice = null;
            }

        } else {
            Toast.makeText(getApplicationContext(), "No Posture Repair sensor to disconnect", Toast.LENGTH_LONG).show();
        }
    }



    /**
     * Runnable class wrapped in a thread objectthat will take an incoming data stream from a
     * connected bluetooth device, and call the parsing routines to generate JSON messages
     */

     private void commsLoop()
     {

            try {
                commSocket = btDevice.createRfcommSocketToServiceRecord(COMMS_UUID);

                commSocket.connect();
                commInputStream = commSocket.getInputStream();

                isRunning = true;
                double sendArray[] = new double[3];
                int sizeReceived;
                byte[] receiveBuffer = new byte[2048];
                String dataReceived = "";

                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Connected to Posture Repair Sensor", Toast.LENGTH_LONG).show();
                    }
                });

                while (isRunning && (sizeReceived = commInputStream.read(receiveBuffer, 0, commInputStream.available())) != -1) {
                    dataReceived = dataReceived.concat(new String(Arrays.copyOfRange(receiveBuffer, 0, sizeReceived), Charset.forName("UTF-8")));

                    while (dataReceived.contains("{") && dataReceived.contains("}")) {
                        int open = dataReceived.indexOf("{");
                        int close = dataReceived.indexOf("}");

                        String jsonObject = dataReceived.substring(open, close + 1);

                        try {
                            Sample newSample = gson.fromJson(jsonObject, Sample.class);



                            if (newSample.getStatus())
                            {

                                sendArray[0] = newSample.getX();
                                sendArray[1] = newSample.getY();
                                sendArray[2] = newSample.getZ();

                                Intent data = new Intent(getApplicationContext(), MainActivity.class);
                                data.setAction(CommsConstants.ACTION_SAMPLE_DATA);

                                data.putExtra(CommsConstants.INTENT_SAMPLE_DATA, sendArray);

                                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(data);

                            }
                            else
                            {
                                Intent configStatus = new Intent(getApplicationContext(), MainActivity.class);
                                configStatus.setAction(CommsConstants.ACTION_BNO055_CONF);

                                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(configStatus);
                            }

                        } catch (Exception e) {
                            Log.i("PostureRepair", "Could not parse message:" + dataReceived);
                        }

                        dataReceived = dataReceived.replace(jsonObject, "");
                    }

                }
                isRunning = false;
                commInputStream.close();
                commSocket.close();
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Disconnected from Posture Repair sensor", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                try {
                    isRunning = false;
                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Error in connection to Posture Repair sensor", Toast.LENGTH_LONG).show();
                        }
                    });

                    commInputStream.close();
                    commSocket.close();


                } catch (Exception ex) {
                    Log.e("PostureRepair", "Error closing reader and socket:  " + e.getMessage());
                }
                Log.e("PostureRepair", "There was an error on the Incoming Data thread: " + e.getMessage());
            }


    }
}
