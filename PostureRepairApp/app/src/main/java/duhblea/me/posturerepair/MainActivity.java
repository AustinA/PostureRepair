package duhblea.me.posturerepair;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

/**
 * Gather and store Posture Repair sensor data.  Vibrate the user if bad posture is maintained
 * for too long
 *
 * @author Austin Alderton
 * @version 12 November 2016
 */
public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener {

    // Constants
    private static final int REQUEST_ENABLE_BT = 31;
    private static final UUID COMMS_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final String BT_MAC_ADDRESS = "00:06:66:69:74:10";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss:SSS");

    // Communication object declarations
    private BluetoothAdapter btAdapter;
    private BluetoothDevice btDevice = null;
    private BluetoothSocket commSocket = null;
    private InputStream commInputStream = null;

    // Data storage and parsing
    Gson gson = new Gson();
    private HashMap<Long, Sample> receivedSamples = null;
    private SampleDatabaseHelper dbHelper = null;

    // Data reception and power management
    private PowerManager powerManager = null;
    private PowerManager.WakeLock wakeLock = null;

    // Main user interface looper for side thread posting to UI
    private Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    // Thread on which communication is performed
    Thread incomingData = null;

    // UI declarations
    private String savedInput = "";
    private VisualMode visualMode = VisualMode.CURRENT_POSITION;
    private boolean snapToBottom = true;

    private TextView savedOutputTextView = null;
    private ScrollView savedOutputScrollView = null;
    private LinearLayout buttom_Buttons = null;
    private Button snapButton = null;
    private Button clearHistory = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Basic UI functionality instantations
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Initialize the DB helper and set the received Samples stored in the database
        dbHelper = new SampleDatabaseHelper(MainActivity.this);
        receivedSamples = dbHelper.readSamples();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PostureRepairWakeLock");




        // Application specific instantation
        initializeUI();

        // Set the start up visual mode
        setVisualMode(VisualMode.CURRENT_POSITION);

    }

    @Override
    protected void onPause()
    {
        super.onPause();


    }

    @Override
    public void onResume()
    {
        if (wakeLock.isHeld())
        {
            wakeLock.release();
        }

        wakeLock.acquire();
        super.onResume();

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_connect) {
            initializeBluetooth();
            return true;
        } else if (id == R.id.action_disconnect) {
            disconnectBluetooth();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.current_position) {
            setVisualMode(VisualMode.CURRENT_POSITION);
        } else if (id == R.id.live_input) {
            setVisualMode(VisualMode.LIVE_INPUT);
        } else if (id == R.id.collected_data) {
            setVisualMode(VisualMode.COLLECTED_DATA);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // interrupt and join the thread when the app is destroyed
        if (incomingData != null) {
            try {
                incomingData.interrupt();
                incomingData.join(1000);

                dbHelper.writeSamples(receivedSamples);

                if (wakeLock.isHeld())
                {
                    wakeLock.release();
                }


            } catch (Exception e) {
                Log.e("PostureRepair", "Closing thread in onDestroy: " + e.getMessage());
            }
        }
    }

    /**
     * Initialize Bluetooth
     */
    private void initializeBluetooth() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter != null) {
            // If Bluetooth not enabled, send the user to the system settings.
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                if (btAdapter.getBondedDevices() != null && !btAdapter.getBondedDevices().isEmpty()) {
                    Object[] connectedDevices = btAdapter.getBondedDevices().toArray();

                    for (int i = 0; i < connectedDevices.length; i++) {
                        BluetoothDevice device = (BluetoothDevice) connectedDevices[i];

                        if (device.getAddress().equals(BT_MAC_ADDRESS)) {
                            btDevice = device;

                            if (incomingData == null) {
                                incomingData = new Thread(new IncomingData());
                                incomingData.start();

                            } else {
                                try {
                                    incomingData.interrupt();
                                } catch (Exception e) {
                                    Log.e("PostureRepair", "Unable to join started thread: " + e.getMessage());
                                }

                                incomingData = new Thread(new IncomingData());
                                incomingData.start();

                            }

                            break;
                        }

                    }


                } else {
                    Toast.makeText(MainActivity.this, "Posture Repair sensor not found", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(MainActivity.this, "Current device does not support Bluetooth "
                            + "This app will not work without Bluetooth",
                    Toast.LENGTH_LONG).show();
        }

    }

    /**
     * Disconnect a Posture Repair Sensor
     */
    private void disconnectBluetooth() {
        if (btDevice != null) {
            if (incomingData != null) {
                try {
                    incomingData.interrupt();


                } catch (Exception e) {
                    Log.e("PostureRepair", "Error joining thread: " + e.getMessage());
                }

                btDevice = null;
            }
        } else {
            Toast.makeText(MainActivity.this, "No Posture Repair sensor to disconnect", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Set the current visual mode of the application
     *
     * @param newMode
     */
    private void setVisualMode(VisualMode newMode) {
        if (newMode == VisualMode.CURRENT_POSITION) {
            savedOutputTextView.setVisibility(View.GONE);
            savedOutputScrollView.setVisibility(View.GONE);
            buttom_Buttons.setVisibility(View.GONE);
            snapButton.setVisibility(View.GONE);
            clearHistory.setVisibility(View.GONE);

            visualMode = newMode;
        } else if (newMode == VisualMode.LIVE_INPUT) {
            savedOutputTextView.setVisibility(View.VISIBLE);
            savedOutputScrollView.setVisibility(View.VISIBLE);

            buttom_Buttons.setVisibility(View.VISIBLE);
            snapButton.setVisibility(View.VISIBLE);
            clearHistory.setVisibility(View.VISIBLE);

            savedOutputTextView.setText(savedInput);

            visualMode = newMode;
        } else if (newMode == VisualMode.COLLECTED_DATA) {
            savedOutputTextView.setVisibility(View.GONE);
            savedOutputScrollView.setVisibility(View.GONE);
            buttom_Buttons.setVisibility(View.GONE);
            snapButton.setVisibility(View.GONE);
            clearHistory.setVisibility(View.GONE);

            visualMode = newMode;
        }
    }

    /**
     * Interface instantation specific to Posture Repair and its functionality
     */
    private void initializeUI() {
        savedOutputTextView = (TextView) findViewById(R.id.terminal_out);
        savedOutputScrollView = (ScrollView) findViewById(R.id.terminal_scrollView);
        buttom_Buttons = (LinearLayout) findViewById(R.id.live_input_buttons);
        snapButton = (Button) findViewById(R.id.snap_bottom_free_btn);
        clearHistory = (Button) findViewById(R.id.clear_raw_input);

        snapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snapToBottom = !snapToBottom;

                if (snapToBottom)
                {
                    snapButton.setText("Disable Autoscrolling");
                }
                else
                {
                    snapButton.setText("Enable Autoscrolling");
                }
            }
        });

        clearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                savedInput = "";
                savedOutputTextView.setText("");
            }
        });

    }


    /**
     * Runnable class wrapped in a thread objectthat will take an incoming data stream from a
     * connected bluetooth device, and call the parsing routines to generate JSON messages
     */
    class IncomingData implements Runnable {
        @Override
        public void run() {
            try {
                commSocket = btDevice.createRfcommSocketToServiceRecord(COMMS_UUID);
                commSocket.connect();
                commInputStream = commSocket.getInputStream();

                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Connected to Posture Repair sensor", Toast.LENGTH_LONG).show();
                    }
                });

                int sizeReceived;
                byte[] receiveBuffer = new byte[2048];
                String dataReceived = "";
                while ((sizeReceived = commInputStream.read(receiveBuffer, 0, commInputStream.available())) != -1 && !Thread.currentThread().isInterrupted()) {
                    dataReceived = dataReceived.concat(new String(Arrays.copyOfRange(receiveBuffer, 0, sizeReceived), Charset.forName("UTF-8")));

                    while (dataReceived.contains("{") && dataReceived.contains("}")) {
                        int open = dataReceived.indexOf("{");
                        int close = dataReceived.indexOf("}");

                        String jsonObject = dataReceived.substring(open, close + 1);

                        try {
                            Sample newSample = gson.fromJson(jsonObject, Sample.class);

                            if (newSample != null) {
                                long sampledTime = Calendar.getInstance().getTimeInMillis();
                                receivedSamples.put(sampledTime, newSample);

                                savedInput = savedInput + DATE_FORMAT.format(new Date(sampledTime)) +
                                        "  " + newSample.toString();
                                uiThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (visualMode == VisualMode.LIVE_INPUT) {
                                            savedOutputTextView.setText(savedInput);

                                            if (snapToBottom) {
                                                savedOutputScrollView.fullScroll(View.FOCUS_DOWN);
                                            }
                                        }
                                    }
                                });
                            }

                        } catch (Exception e) {
                            Log.i("PostureRepair", "Could not parse message:" + dataReceived);
                        }

                        dataReceived = dataReceived.replace(jsonObject, "");
                    }

                }

                commInputStream.close();
                commSocket.close();
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Disconnected from Posture Repair sensor", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                try {
                    commInputStream.close();
                    commSocket.close();

                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Disconnected from Posture Repair sensor", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception ex) {
                    Log.e("PostureRepair", "Error closing reader and socket:  " + e.getMessage());
                }
                Log.e("PostureRepair", "There was an error on the Incoming Data thread: " + e.getMessage());
            }

        }
    }
}
