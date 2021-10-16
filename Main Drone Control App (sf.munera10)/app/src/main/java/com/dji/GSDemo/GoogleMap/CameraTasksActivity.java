package com.dji.GSDemo.GoogleMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;

import android.view.View;

import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;


import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;

import dji.common.mission.waypoint.Waypoint;

import static com.dji.GSDemo.GoogleMap.MainActivity.checkGpsCoordination;

public class CameraTasksActivity extends AppCompatActivity implements View.OnClickListener, OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private GoogleMap mMap;
    private Button done, cancel;

    private double droneLat;
    private double droneLng;
    private ArrayList<LatLng> pathWay;
    private ArrayList<Marker> mMarkers;
    private ArrayList<String> markerIds;
    private boolean videoStarted;

    private MainActivity.TASK cameraTask;

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.tasks_map);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        droneLat = getIntent().getExtras().getDouble("ID-DRONE_LAT");
        droneLng = getIntent().getExtras().getDouble("ID-DRONE_LNG");
        pathWay =  (ArrayList<LatLng>) getIntent().getExtras().get("ID-PATHWAY");
        cameraTask = MainActivity.TASK.NONE;
        videoStarted = false;
        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.tasksMap);
        mapFragment.getMapAsync(this);

    }

    private void initUI() {
        final RadioGroup tasks_RG1 =  findViewById(R.id.tasks1);
        final RadioGroup tasks_RG2 = findViewById(R.id.tasks2);

        tasks_RG1.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.tasksPicture) {
                    cameraTask = MainActivity.TASK.PICTURE;
                    tasks_RG2.clearCheck();
                } else if (checkedId == R.id.tasksVideo) {
                    cameraTask = MainActivity.TASK.VIDEO;
                    tasks_RG2.clearCheck();
                }

            }
        });

        tasks_RG2.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.tasksPanoramic) {
                    cameraTask = MainActivity.TASK.PANORAMIC;
                    tasks_RG1.clearCheck();
                } else if (checkedId == R.id.tasksInterval) {
                    cameraTask = MainActivity.TASK.INTERVAL;
                    tasks_RG1.clearCheck();
                }

            }
        });


        cancel = findViewById(R.id.btn_cancel);
        done = findViewById(R.id.btn_done);

        cancel.setOnClickListener(this);
        done.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.btn_cancel: {

                Intent returnIntent = new Intent();
                setResult(Activity.RESULT_CANCELED, returnIntent);
                finish();
                break;

            }
            case R.id.btn_done: {

                getChosenIDs();
                Intent returnIntent = new Intent();
                returnIntent.putExtra("MARKED-WAYPOINTS", markerIds);
                //returnIntent.putExtra("CHOSEN-TASK", cameraTask);
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
                break;
            }
        }


    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (mMap == null) {
            mMap = googleMap;
            //setUpMap();
        }
        mMap.setOnMarkerClickListener(this);
        LatLng shenzhen = new LatLng(22.5362, 113.9454);
        mMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));


        updateDroneLocation();
        markPath();

    }

    private void updateDroneLocation(){

        LatLng pos = new LatLng(droneLat, droneLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (checkGpsCoordination(droneLat, droneLng)) {
                    mMap.addMarker(markerOptions);
                }
            }
        });

        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        mMap.moveCamera(cu);
    }


    private void markPath() {
        mMarkers = new ArrayList<Marker>();
        markerIds = new ArrayList<String>();
        for (int i = 0; i < pathWay.size(); i++) {
            LatLng pointer = pathWay.get(i);
            if((i+1)!=pathWay.size()){
                LatLng pointer2 = pathWay.get(i+1);
                Polyline polyline = mMap.addPolyline(new PolylineOptions()
                        .add(pointer, pointer2)
                        .width(5)
                        .color(Color.CYAN));
            }
            markWaypoint(pathWay.get(i), i + 1);
        }
    }

    private void markWaypoint(LatLng point, int id){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET));
        Marker marker = mMap.addMarker(markerOptions);
        marker.setTag(MainActivity.TASK.NONE.toString());
        mMarkers.add(marker);
    }

    private void getChosenIDs(){
        for (Marker mMarker: mMarkers){
            if (!mMarker.getTag().equals(MainActivity.TASK.NONE.toString())){
                String indicador = mMarker.getId() + "-" + mMarker.getTag();
                markerIds.add(indicador);
            }
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

        }
    };

    private void showToast(final String toastMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();

            }
        });
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        String tag = (marker.getTag().toString()) ;
        switch (cameraTask) {
            case PICTURE:
                checkVideoSelectedPreviously(tag);
                processSelection(cameraTask.toString(), tag, marker, BitmapDescriptorFactory.HUE_BLUE);
                break;

            case VIDEO:
                checkVideoSelectedPreviously(tag);
                processSelection(cameraTask.toString(), tag, marker, BitmapDescriptorFactory.HUE_RED);
                videoStarted = !videoStarted;
                break;

            case INTERVAL:
                checkVideoSelectedPreviously(tag);
                processSelection(cameraTask.toString(), tag, marker, BitmapDescriptorFactory.HUE_ORANGE);
                break;

            case PANORAMIC:
                checkVideoSelectedPreviously(tag);
                processSelection(cameraTask.toString(), tag, marker, BitmapDescriptorFactory.HUE_YELLOW);
                break;

            case NONE:
                showToast("Please select a Task");
                break;
        }
    return true;
    }

    private void checkVideoSelectedPreviously(String tag){
        if(tag.equals(MainActivity.TASK.VIDEO.toString())) {
            videoStarted = !videoStarted;
        }
    }

    private void processSelection(String task, String tag, Marker marker, Float color){
        if(task.equals(tag)){
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET));
            marker.setTag(MainActivity.TASK.NONE.toString());
        }
        else {
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(color));
            marker.setTag(task);
        }
    }
}
