package com.gogo.georges.gotolocation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.support.wearable.activity.*;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.EventListener;
import java.util.ResourceBundle;

public class MainWear extends WearableActivity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks
        , GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener, DataApi.DataListener, NodeApi.NodeListener, LocationListener, View.OnClickListener{



    /////////////////////////////////////////////
    private TextView showMeter;
    private int desCount = 0;
    private View pointerArrow;
    private float bearing = 0;
    private float rotateAngel;
    private Button stopB;

    //////////compass variables//////////
    private SensorManager sensorManager = null;
    private Sensor gravitySensor;
    private Sensor magnetSensor;

    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    private float azimuth = 0f;
    private float currectAzimuth = 0;

    private View compassArrow;
    ///google clientapi
    private GoogleApiClient myGoogleAPI;


    private float heading = 0;
    private boolean vibrateCheck = false;


    private Vibrator wearVibrate;
    ////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_wear);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener()
        {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                showMeter = (TextView) stub.findViewById(R.id.meterShow);

                compassArrow = (View) stub.findViewById(R.id.compassArrow);
                pointerArrow = (View) stub.findViewById(R.id.pointArrow);

                createCompassAnimation();
                createArrowRotation();
                CreateDistanceCounter();
                setAmbientEnabled();

            }
        });

        ///////////////////////////////////////////
        this.sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        this.gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        this.wearVibrate = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);


        /// register receiver
        IntentFilter msgFilter = new IntentFilter(Intent.ACTION_SEND);
        MessageReceiver messageRec = new MessageReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageRec, msgFilter);


        this.myGoogleAPI = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(this)
                .build();
        myGoogleAPI.connect();

    }


    ////////////create the compass animation / rotation
    public void createCompassAnimation()
    {
        Animation myAnim = new RotateAnimation(-this.currectAzimuth, -this.azimuth, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        this.currectAzimuth = this.azimuth;

        myAnim.setDuration(500);
        myAnim.setRepeatCount(0);
        myAnim.setFillAfter(true);

        if(compassArrow != null)
        {
            this.compassArrow.startAnimation(myAnim);
        }

    }

    void createOnClick()
    {
        stopB.setOnClickListener(this);
    }
    //////////////create Arrow animation
    public void createArrowRotation()
    {
        ///// arrowPointer //////
        if (pointerArrow != null)
        {
            this.rotateAngel = (bearing - azimuth);
            this.pointerArrow.setRotation(rotateAngel);
        }
    }

    /////////////create the compmass data
    private void createCompasspoints(SensorEvent event)
    {
        final float alpha = 0.97f;

        synchronized (this)
        {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            {
                mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0];
                mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1];
                mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2];
            }

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            {
                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0];
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1];
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2];
            }

            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, this.mGravity, this.mGeomagnetic);
            if (success == true)
            {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);

                this.azimuth = (float)Math.toDegrees(orientation[0]);

                createCompassAnimation();
                createArrowRotation();
            }
        }

    }

    private void vibrateHandler()
    {
        long[] pattern = {400, 500};
        if (this.desCount <= 100 && desCount > 50)
        {
            this.wearVibrate.vibrate(pattern, 0);
            vibrateCheck = false;
            stopB.setVisibility(View.GONE);
        }

        if (this.desCount <= 50 && desCount > 25)
        {
            pattern[0] = 300;
            pattern[1] = 400;
            wearVibrate.vibrate(pattern, 0);
            vibrateCheck = false;
            stopB.setVisibility(View.GONE);
        }

        if (desCount <= 25 && vibrateCheck == false)
        {
            //pattern[0] = 120;
            //pattern[1] = 200;
            //wearVibrate.vibrate(pattern, 0);
            wearVibrate.cancel();
            vibrateCheck = true;
            Toast.makeText(MainWear.this, "Too Close to destination!!", Toast.LENGTH_SHORT).show();

        }
        else if (desCount > 100)
        {
            wearVibrate.cancel();
            stopB.setVisibility(View.GONE);
            vibrateCheck = false;
        }



    }



    public void CreateDistanceCounter() {
        this.showMeter.setText(this.desCount + " meters");
        if (desCount == 0)
        {
            vibrateCheck = true;
        }
        vibrateHandler();
    }

    @Override
    public void onConnected(Bundle bundle)
    {
        Wearable.DataApi.addListener(this.myGoogleAPI, MainWear.this);
        Wearable.MessageApi.addListener(this.myGoogleAPI, this);
        Wearable.NodeApi.addListener(this.myGoogleAPI, this);

    }

    @Override
    protected void onDestroy()
    {
        sensorManager.unregisterListener(this);
        this.myGoogleAPI.disconnect();
        super.onDestroy();
        wearVibrate.cancel();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        this.myGoogleAPI.connect();
        sensorManager.registerListener(this, this.gravitySensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, this.magnetSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        this.myGoogleAPI.disconnect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.myGoogleAPI.disconnect();
    }

    @Override
    public void onConnectionSuspended(int i)
    {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    @Override
    public void onLocationChanged(Location location)
    {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider)
    {

    }

    @Override
    public void onProviderDisabled(String provider)
    {


    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {


    }

    @Override
    public void onPeerConnected(Node node) {
    }

    @Override
    public void onPeerDisconnected(Node node) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        createCompasspoints(event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails)
    {
        super.onEnterAmbient(ambientDetails);
        showMeter.getPaint().setAntiAlias(false);
    }

    @Override
    public void onClick(View v)
    {
        if (v.equals(stopB))
        {
            wearVibrate.cancel();
            vibrateCheck = true;
        }
    }


    //////////reaceiver class///////////
    public class MessageReceiver extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            desCount = (int)intent.getDoubleExtra("destan", 0);
            bearing = (float)intent.getDoubleExtra("beard", 0);
            CreateDistanceCounter();
            createArrowRotation();

        }
    }





}
