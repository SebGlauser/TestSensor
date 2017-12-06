package com.example.testsensor;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.microchip.android.mcp2221comm.Mcp2221Comm;
import com.microchip.android.mcp2221comm.Mcp2221Config;
import com.microchip.android.mcp2221comm.Mcp2221Constants;
import com.microchip.android.microchipusb.Constants;
import com.microchip.android.microchipusb.MCP2221;

import java.nio.IntBuffer;

public class MainActivity extends AppCompatActivity {

    /** Microchip Product and Vendor ID. */
    protected static final int MCP2221_PID = 0xDD;
    protected static final int MCP2221_VID = 0x4D8;

    /** USB permission action for the USB broadcast receiver. */
    private static final String ACTION_USB_PERMISSION = "com.microchip.android.USB_PERMISSION";

    /** Board access variables */
    public MCP2221 mcp2221;
    public Mcp2221Comm mcp2221Comm;
    private PendingIntent mPermissionIntent;

    /** Button view */
    private Button mButtonLedGreen, mButtonLedOrange;
    private ProgressBar mSensorProgressBar;
    private ProgressBar mReferenceProgressBar;
    private TextView mTvSensor, mTvRef;


    /** Activity variables */
    private boolean mButtonLedGreenStatus  = false;
    private boolean mButtonLedOrangeStatus = false;
    private SeekBarAsyncTaskRunner mSeekBarAsyncUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get view items
        mButtonLedGreen = findViewById(R.id.led_green);
        mButtonLedOrange = findViewById(R.id.led_orange);
        mSensorProgressBar = findViewById(R.id.progressbar);
        mReferenceProgressBar = findViewById(R.id.progressbar2);
        mTvRef = findViewById(R.id.text_ref);
        mTvSensor = findViewById(R.id.text_sensor);

        // Launch permission intent
        mPermissionIntent =
                PendingIntent.getBroadcast(this,
                        0,
                        new Intent(ACTION_USB_PERMISSION),
                        0);
        final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        registerReceiver(mUsbReceiver, filter);

        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbReceiver, filter);

        // Get access to the board
        mcp2221 = new MCP2221(this);

        // Set listener
        mButtonLedGreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toogle_led_green();
            }
        });

        mButtonLedOrange.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toogle_led_yellow();
            }
        });
    }

    /**
     * Change the led orange value
     */
    private void toogle_led_yellow() {
        // If the connection present
        if (mcp2221Comm != null){
            mButtonLedOrangeStatus = !mButtonLedOrangeStatus;
            if(mButtonLedOrangeStatus) {
                mcp2221Comm.setGpPinValue((byte) 0x00, (byte) 0x01);
            }
            else{
                mcp2221Comm.setGpPinValue((byte) 0x00, (byte) 0x00);
            }
        }
    }

    private Integer read_sensor_value(){
        Integer value = new Integer(0);
        if(mcp2221Comm != null) {
            IntBuffer adcData = IntBuffer.allocate(3);
            if (mcp2221Comm.getAdcData(adcData) == Mcp2221Constants.ERROR_SUCCESSFUL) {
                value = adcData.get(1);
            }
        }
        return value;
    }

    private Integer read_reference_value(){
        Integer value = new Integer(0);
        if(mcp2221Comm != null) {
            IntBuffer adcData = IntBuffer.allocate(3);
            if (mcp2221Comm.getAdcData(adcData) == Mcp2221Constants.ERROR_SUCCESSFUL) {
                value = adcData.get(2);
            }
        }
        return value;
    }


    /**
     * Change the led green value
     */
    private void toogle_led_green() {
        // If the connection present
        if (mcp2221Comm != null){
            mButtonLedGreenStatus = !mButtonLedGreenStatus;
            if(mButtonLedGreenStatus) {
                mcp2221Comm.setGpPinValue((byte) 0x01, (byte) 0x01);
            }
            else{
                mcp2221Comm.setGpPinValue((byte) 0x01, (byte) 0x00);
            }
        }
    }

    /**
     * Broadcast receiver USB Connection
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    final UsbDevice device =
                            (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    // is usb permission has been granted, try to open a connection
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,
                            false)) {
                        if (device != null) {
                            // call method to set up device communication
                            final Constants result = mcp2221.open();

                            if (result != Constants.SUCCESS) {
                                messageToast("Could not open MCP2221 connection");
                                mcp2221Comm = null;
                                
                            } else {
                                mcp2221Comm = new Mcp2221Comm(mcp2221);
                                messageToast("MCP2221 connection opened");
                                Handler myHandler = new Handler();
                                //this waits 3 seconds, then will call the run() method below.
                                myHandler.postDelayed(pinconfig, 1000);
                                
                            }
                        }
                    } else {
                        messageToast("USB Permission Denied");
                        mcp2221Comm = null;
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                messageToast("Device Detached");
                if(mSeekBarAsyncUpdater != null) {
                    mSeekBarAsyncUpdater.cancel(true);
                }
                // close the connection and
                // release all resources
                mcp2221.close();
                // leave a bit of time for the COM thread to close
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                }
                mcp2221Comm = null;

            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                messageToast("Device Attached");
                
                final UsbDevice device =
                        (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {

                    // only try to connect if an MCP2221 is attached
                    if (device.getVendorId() == MCP2221_VID && device.getProductId() == MCP2221_PID) {
                        final Constants result = mcp2221.open();

                        switch (result) {
                            case SUCCESS:
                                messageToast("MCP2221 Connection Opened");
                                
                                mcp2221Comm = new Mcp2221Comm(mcp2221);

                                Handler myHandler = new Handler();
                                myHandler.postDelayed(pinconfig, 1000);

                                break;
                            case CONNECTION_FAILED:
                                messageToast("Connection Failed");
                                mcp2221Comm = null;
                                break;
                            case NO_USB_PERMISSION:
                                mcp2221Comm = null;
                                mcp2221.requestUsbPermission(mPermissionIntent);
                                break;
                            default:
                                break;
                        }
                    }
                }
            }

        }
    };

    private Runnable pinconfig = new Runnable() {
        @Override
        public void run() {
            // Build a configuration
            Mcp2221Config mMcp2221Config = new Mcp2221Config();

            // If the connection present
            if (mcp2221Comm != null){

                // Set the pin destinations
                mMcp2221Config.setGpPinDesignations(new byte[]{
                        0, // GPIO0 : 0 = GPIO operation                ///< LED ORANGE
                        0, // GPIO1 : 0 = GPIO operation                ///< LED GREEN
                        2, // GPIO2 : 2 = Alternate function 0 (ADC2)   ///< SENSOR VALUE
                        2  // GPIO3 : 0 = Alternate function 0 (ADC2)   ///< REF
                });

                // Set the pin direction
                mMcp2221Config.setGpPinDirections(new byte[]{
                        0, // GPIO0 : 0 = OUTPUT
                        0, // GPIO1 : 0 = OUTPUT
                        1, // GPIO2 : 1 = INPUT
                        1  // GPIO3 : 1 = INPUT
                });

                mMcp2221Config.setAdcVoltageReference((byte) 0x00);

                // Send the configuration
                if (mcp2221Comm.setSRamSettings(mMcp2221Config,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true) == Mcp2221Constants.ERROR_SUCCESSFUL) {
                    mSeekBarAsyncUpdater = new SeekBarAsyncTaskRunner();
                    mSeekBarAsyncUpdater.execute();
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mSeekBarAsyncUpdater != null) {
            mSeekBarAsyncUpdater.cancel(true);
        }
        // close the connection and release all resources
        unregisterReceiver(mUsbReceiver);
    }

    /**
     * Send a message toast
     * @param content The content of the message
     */
    private void messageToast(String content) {
        Toast.makeText(this, content, Toast.LENGTH_SHORT).show();
        Log.d("MainActivity", content);
    }

    /**
     * Inner class to update the seek bar while the music is playing
     */
    private class SeekBarAsyncTaskRunner extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            // The isCancelled is needed if you want to kill the thread
            while (mcp2221Comm != null && !isCancelled()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (mcp2221Comm != null ) {
                    publishProgress(read_sensor_value(),read_reference_value());
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (mcp2221Comm != null) {
                mSensorProgressBar.setProgress(progress[0]);
                mTvRef.setText("Ref Value : "
                        + String.format("%.3f", ((double)progress[1]) * 5. /1024.)
                        +"V" );
                mReferenceProgressBar.setProgress(progress[1]);
                mTvSensor.setText("Sensor Value : "
                        + String.format("%.3f", ((double)progress[0]) * 5. /1024.)
                        +"V" );
            }
        }

    }
}
