package com.gogo.georges.gotolocation;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.HashSet;

public class MainMap extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener
        , GoogleApiClient.ConnectionCallbacks, GoogleMap.OnMyLocationChangeListener, GoogleMap.OnMapLongClickListener,
        SensorEventListener, NodeApi.NodeListener, DataApi.DataListener, MessageApi.MessageListener
{


    private MarkerOptions newMarker;
    private LatLng selectedPosition;
    private Location myLococation;
    private Location selectedLocation;
    private double desBetween = 0;

    private GoogleApiClient myGoogleAPI;
    private String wearPath = "/wear-path";

    private NodeApi.NodeListener nodeListener;
    private Handler handler;
    private MessageApi.MessageListener messageListener;
    private String nodeId = "";

    private float bearing = 0;
    private Boolean positionSelected = false;

    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        this.handler = new Handler();
        this.newMarker = new MarkerOptions();

        createGoogelAPI();
        createNodeListener();
        setUpMapIfNeeded();

        LocationRequest myLoca = LocationRequest.create();
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

    @Override
    protected void onStart()
    {
        super.onStart();
        this.myGoogleAPI.connect();
        mMap.setOnMyLocationChangeListener(this);
    }


    /// create the nodeLsitener for the DataAPI
    private void createNodeListener()
    {
        // create node listener
        this.nodeListener = new NodeApi.NodeListener()
        {
            @Override
            public void onPeerConnected(Node node)
            {
                nodeId = node.getId();
                handler.post(new Runnable() {
                    @Override
                    public void run()
                    {
                        Toast.makeText(MainMap.this, "enabled!!", Toast.LENGTH_SHORT).show();
                    }
                });

            }

            @Override
            public void onPeerDisconnected(Node node)
            {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainMap.this, "disabled", Toast.LENGTH_SHORT).show();
                    }
                });
            }

        };
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        mMap.setOnMapLongClickListener(MainMap.this);

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(62, 15);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        mMap.setMyLocationEnabled(true);
    }

    ///// crete map if needed
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap()
    {
    }


    /// create the google api
    private void createGoogelAPI()
    {
        this.myGoogleAPI = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApiIfAvailable(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }
    public void createMarker(LatLng point) {
        mMap.clear();
        this.newMarker.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        mMap.addMarker(this.newMarker.position(point).title("Destination"));
    }

    private void createTheDataMapAndSend()
    {
        PutDataMapRequest putDMapR = PutDataMapRequest.create(this.wearPath);
        putDMapR.getDataMap().putDouble("dest", this.desBetween);
        putDMapR.getDataMap().putDouble("bear", this.bearing);
        PutDataRequest putDReq = putDMapR.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(myGoogleAPI, putDReq);
    }

    private void calculateDestances()
    {
        if (this.positionSelected == true && mMap.isMyLocationEnabled() != false)
        {
            this.selectedPosition = this.newMarker.getPosition();
            // destination position
            this.selectedLocation = new Location("destlocation");
            this.selectedLocation.setLatitude(this.selectedPosition.latitude);
            this.selectedLocation.setLongitude(this.selectedPosition.longitude);

            // my location
            this.myLococation = new Location("My Location");
            this.myLococation.setLatitude(mMap.getMyLocation().getLatitude());
            this.myLococation.setLongitude(mMap.getMyLocation().getLongitude());

            // now calculate the destances between the two locations
            this.desBetween = this.myLococation.distanceTo(this.selectedLocation);
        }
    }


    private void calculateBearing()
    {
        if (this.positionSelected == true)
        {
            this.desBetween = (int) this.myLococation.distanceTo(this.selectedLocation);
            this.bearing = this.myLococation.bearingTo(this.selectedLocation);
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        this.myGoogleAPI.disconnect();
    }


    @Override
    public void onConnected(Bundle bundle)
    {
        /// the handheld devices need no listener from Data/Node API
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        if (null != this.myGoogleAPI && this.myGoogleAPI.isConnected())
        {
            this.myGoogleAPI.disconnect();
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer)
    {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {

    }

    @Override
    public void onPeerConnected(Node node)
    {

    }

    @Override
    public void onPeerDisconnected(Node node)
    {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult)
    {
        if (connectionResult.getErrorCode() == ConnectionResult.API_UNAVAILABLE)
        {
            Toast.makeText(MainMap.this, "Wearable api unavalble", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPostResume()
    {
        super.onPostResume();

        // check is google service avalble
        int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

        if (connectionResult != ConnectionResult.SUCCESS)
        {
            // google play servcie is not avalble
            GooglePlayServicesUtil.showErrorDialogFragment(connectionResult, this, 0, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog)
                {
                    finish();
                }
            });
        }
        else
        {
            myGoogleAPI.connect();
        }
    }


    @Override
    protected void onPause()
    {
        // Unregister Node and Message listeners, disconnect GoogleApiClient and disable buttons
        super.onPause();
    }


    public void onMapLongClick(LatLng point)
    {
        if (mMap.getMyLocation() != null)
        {
            this.positionSelected = true;
            createMarker(point);
            calculateDestances();
            calculateBearing();
            createTheDataMapAndSend();
            onSendDataToWear();
        }
        else
        {
            Toast.makeText(MainMap.this, "Your location is not availeble!!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMyLocationChange(Location location)
    {
        if (location != mMap.getMyLocation())
        {
            calculateDestances();
            calculateBearing();
            createTheDataMapAndSend();
            onSendDataToWear();
        }

    }

    private void sendStartActivity(String node)
    {
        Wearable.MessageApi.sendMessage(myGoogleAPI, node, wearPath, new byte[0]);
    }

    private Collection<String> getNodes()
    {
        HashSet<String> results = new HashSet<>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(myGoogleAPI).await();

        for (Node node : nodes.getNodes())
        {
            results.add(node.getId());
        }

        return results;
    }


    public void onSendDataToWear()
    {
        // Trigger an AsyncTask that will query for a list of connected nodes and send
        // "Information" to each connected node.
        new StartWearActivity().execute();
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }




    /////// class to send Data to Wear ///////
    private class StartWearActivity extends AsyncTask<Void, Void, Void>
    {

        @Override
        protected Void doInBackground(Void... params)
        {
            Collection<String> nodes = getNodes();
            for (String node : nodes)
            {
                sendStartActivity(node);
            }
            return null;
        }
    }


}
