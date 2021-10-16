package com.dji.GSDemo.GoogleMap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.camera.SettingsDefinitions;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, OnMapReadyCallback {

    private Toolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private boolean showsTrace;
    private boolean tasksUploaded;
    //ID of path
    private int IDPATH;

    //Firebase
    FirebaseDatabase database;
    ArrayList<PathPoint> pathFromFirebase;
    final static double PI = 3.14159265358979323846;
    final static double r_earth = 6378000;
    final static int LAUNCH_TASKS_ACTIVITY = 1;

    protected static final String TAG = "GSDemoActivity";

    private GoogleMap gMap;

    private Button locate, toggle;
    private Button start, stop;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private List<Polyline> polylines = new ArrayList<>();
    private Marker droneMarker = null;
    private ArrayList<LatLng> pointers;
    private ArrayList<String> markersIds;

    private float altitude = 10.0f;
    private float mSpeed = 5.0f;

    private LatLng fixedDroneLocation = null;
    private List<Waypoint> waypointList = new ArrayList<>();
    private List<String> traceList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private EditText editTxtSpeed;
    /*Enum para las posibles acciones de la camara*/
    enum TASK {
        PICTURE,
        VIDEO,
        PANORAMIC,
        INTERVAL,
        NONE
    }

    /*Atributos para la descarga de archivos multimedia.*/
    private MediaManager mMediaManager;
    private List<MediaFile> mediaFileList = new ArrayList<MediaFile>();
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/MediaManagerDemo/");


    /*Accion que ejecutara la camara*/
    //private TASK cameraTask;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
        initMediaManager();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        if (mediaFileList != null) {
            mediaFileList.clear();
        }
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {
        showsTrace = true;
        toggle = findViewById(R.id.toggle);
        locate = findViewById(R.id.locate);
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);

        toggle.setOnClickListener(this);
        locate.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        //Retrieve ID of path
        IDPATH = 0;
        IDPATH = getIntent().getExtras().getInt("ID-PATH");

        initUI();

        toolbar = (Toolbar)findViewById(R.id.appbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_burguer);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        navView = (NavigationView)findViewById(R.id.navview);

        navView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.menu_seccion_config:
                                showSettingDialog();
                                break;
                            case R.id.menu_seccion_camera:
                                showMapDialog();
                                break;
                            case R.id.menu_seccion_upload:
                                uploadWayPointMission();
                                break;
                            case R.id.menu_opcion_info:
                                if(!pathFromFirebase.isEmpty() && !waypointList.isEmpty()){
                                    showInfoDialog();
                                }
                                else {
                                    setResultToToast("Path info seems to be empty.");
                                }
                                break;
                            case R.id.menu_opcion_trace:
                                if(!traceList.isEmpty()){
                                    showTraceDialog();
                                }
                                else {
                                    setResultToToast("Drone location info seems to be empty.");
                                }
                                break;
                            case R.id.menu_opcion_livefeed:
                                showLiveFeed();
                                break;
                            case R.id.menu_opcion_acerca:
                                setResultToToast("GSDemo. Developed by Nelson Sánchez & Santiago Múnera.");
                                break;
                        }
                        drawerLayout.closeDrawers();
                        return true;
                    }
                });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        addListener();
        tasksUploaded = false;
        //Message for loadding path
        showToast("Searching for a new path");

        // Get database instance
        database = FirebaseDatabase.getInstance();


        this.retreivePathFromDatabase();


    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
        loginAccount();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {



        BaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation();
                    if(fixedDroneLocation == null){
                        fixedDroneLocation = new LatLng(droneLocationLat, droneLocationLng);
                    }
                }
            });
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null){
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        //TODO Se puede revisar si el dron llego al waypoint y marcar el estado de la tarea
        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {
            if(executionEvent.getProgress().isWaypointReached){
                int id = executionEvent.getProgress().targetWaypointIndex +1;
                traceList.add("\n\nTrace:\n\nID: " + id+
                        "\nLongitude: "+ mFlightController.getState().getAircraftLocation().getLongitude() +
                        "\nLatitude: " + mFlightController.getState().getAircraftLocation().getLatitude()+"\n\n");
            }
        }
        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
            tasksUploaded = false;

            downloadFileByIndex();
            showToast("Terminamos...");
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null){
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markWaypoint(LatLng point, int id){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.title("Id: "+id+"; Lat: "+point.latitude+"; Lon: "+point.longitude);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.start:{
                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            case R.id.toggle:{
                if(showsTrace){
                    showsTrace = false;
                    for(Polyline line : polylines)
                    {
                        line.remove();
                    }
                    polylines.clear();
                }else{
                    showsTrace = true;
                    for(int i = 0;i<pathFromFirebase.size();i++) {
                        Waypoint newWaypont = getWayPointFromPathPointFixed(pathFromFirebase.get(i));
                        LatLng pointer = new LatLng(newWaypont.coordinate.getLatitude(), newWaypont.coordinate.getLongitude());
                        if ((i + 1) != pathFromFirebase.size()) {
                            Waypoint destinyWaypont = getWayPointFromPathPointFixed(pathFromFirebase.get(i + 1));
                            LatLng pointer2 = new LatLng(destinyWaypont.coordinate.getLatitude(), destinyWaypont.coordinate.getLongitude());
                            Polyline polyline = gMap.addPolyline(new PolylineOptions()
                                    .add(pointer, pointer2)
                                    .width(5)
                                    .color(Color.CYAN));
                            polylines.add(polyline);
                        }
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);

    }

    private void showInfoDialog(){
        LinearLayout info = (LinearLayout) getLayoutInflater().inflate(R.layout.path_info, null);
        TextView txtViewPath = info.findViewById(R.id.textViewPath);
        ListView firebase = info.findViewById(R.id.listViewPath);

        List<String> strings1 = new ArrayList<>();
        String title = "Path from Firebase";
        txtViewPath.setText(title);
        for(int i = 0; i<pathFromFirebase.size(); i++){
            double lon = pathFromFirebase.get(i).getXLongitude();
            double lat = pathFromFirebase.get(i).getZLatitude();
            int id = i+1;
            if(lon>= 0 && lat>=0){
                strings1.add("\n\nOriginal\n\n" + "ID: " + id +
                        "\nEast distance: "+pathFromFirebase.get(i).getXLongitude() +
                        " m\nNorth distance: " + pathFromFirebase.get(i).getZLatitude() + " m"
                        + "\n\nTransform\n\nLongitude: "+waypointList.get(i).coordinate.getLongitude() +
                        "\nLatitude: " + waypointList.get(i).coordinate.getLatitude()+"\n\n");
            } else if(lon <0 && lat>=0){
                strings1.add("\n\nOriginal\n\n" + "ID: " + id +
                        "\nWest distance: "+Math.abs(pathFromFirebase.get(i).getXLongitude()) +
                        " m\nNorth distance: " + pathFromFirebase.get(i).getZLatitude() + " m"
                        + "\n\nTransform\n\nLongitude: "+waypointList.get(i).coordinate.getLongitude() +
                        "\nLatitude: " + waypointList.get(i).coordinate.getLatitude()+"\n\n");
            } else if(lon>= 0 && lat<0){
                strings1.add("\n\nOriginal\n\n" + "ID: " + id +
                        "\nEast distance: "+pathFromFirebase.get(i).getXLongitude() +
                        " m\nSouth distance: " + Math.abs(pathFromFirebase.get(i).getZLatitude()) + " m"
                        + "\n\nTransform\n\nLongitude: "+waypointList.get(i).coordinate.getLongitude() +
                        "\nLatitude: " + waypointList.get(i).coordinate.getLatitude()+"\n\n");
            } else{
                strings1.add("\n\nOriginal\n\n" + "ID: " + id +
                        "\nWest distance: "+Math.abs(pathFromFirebase.get(i).getXLongitude()) +
                        " m\nSouth distance: " + Math.abs(pathFromFirebase.get(i).getZLatitude()) + " m"
                        + "\n\nTransform\n\nLongitude: "+waypointList.get(i).coordinate.getLongitude() +
                        "\nLatitude: " + waypointList.get(i).coordinate.getLatitude()+"\n\n");
            }
        }
        ArrayAdapter<String> arrayAdapter
                = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, strings1);

        firebase.setAdapter(arrayAdapter);

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(info)
                .setPositiveButton("OK",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    private void showTraceDialog(){
        LinearLayout info = (LinearLayout) getLayoutInflater().inflate(R.layout.path_info, null);
        TextView txtViewPath = info.findViewById(R.id.textViewPath);
        ListView firebase = info.findViewById(R.id.listViewPath);
        String title = "GPS readings for each path point";
        txtViewPath.setText(title);
        ArrayAdapter<String> arrayAdapter
                = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, traceList);

        firebase.setAdapter(arrayAdapter);

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(info)
                .setPositiveButton("OK",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    private void showSettingDialog(){
        ScrollView wayPointSettings = (ScrollView) getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = wayPointSettings.findViewById(R.id.altitude);
        editTxtSpeed = wayPointSettings.findViewById(R.id.editTxtSpeed);
        RadioGroup speed_RG = wayPointSettings.findViewById(R.id.speed1);
        //TODO Other Radio Group
        RadioGroup actionAfterFinished_RG1 = wayPointSettings.findViewById(R.id.actionAfterFinished1);
        RadioGroup actionAfterFinished_RG2 = wayPointSettings.findViewById(R.id.actionAfterFinished2);
        RadioGroup heading_RG1 =  wayPointSettings.findViewById(R.id.heading1);
        RadioGroup heading_RG2 = wayPointSettings.findViewById(R.id.heading2);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    editTxtSpeed.setText("2.0");
                    mSpeed = 2.0f;
                } else if (checkedId == R.id.MidSpeed){
                    editTxtSpeed.setText("3.5");
                    mSpeed = 3.5f;
                } else if (checkedId == R.id.HighSpeed){
                    editTxtSpeed.setText("5.0");
                    mSpeed = 5.0f;
                }
            }
        });

        actionAfterFinished_RG1.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                }
                else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                }
            }
        });

        actionAfterFinished_RG2.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                }
                else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG1.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");
                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                }
                else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                }
            }
        });

        heading_RG2.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");
                if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });


        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);

                        configWayPointMission();
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    /* Inicia la actividad para seleccionar las tareas que la camara va a realizar*/
    private void showMapDialog(){
        markersIds = new ArrayList<String>();
        Intent mIntent = new Intent(MainActivity.this,CameraTasksActivity.class);
        mIntent.putExtra("ID-DRONE_LAT",droneLocationLat);
        mIntent.putExtra("ID-DRONE_LNG",droneLocationLng);
        mIntent.putExtra("ID-PATHWAY",pointers);
        //startActivity(mIntent);
        startActivityForResult(mIntent, LAUNCH_TASKS_ACTIVITY);
        //ScrollView cameraTasksSetting = (ScrollView) getLayoutInflater().inflate(R.layout.tasks_map, null);
    /*
        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(cameraTasksSetting)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        Log.e(TAG, "Camera task "+cameraTask);
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();*/
    }

    private void showLiveFeed(){
        Intent mIntent = new Intent(MainActivity.this, LiveFeedActivity.class);
        mIntent.putExtra("TASKS", tasksUploaded);
        startActivity(mIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == LAUNCH_TASKS_ACTIVITY) {
            if (resultCode == RESULT_OK) {
                markersIds = data.getStringArrayListExtra("MARKED-WAYPOINTS");
                //cameraTask = (TASK) data.getExtras().get("CHOSEN-TASK");
                tasksUploaded = true;
                setWayPointActions();
                showToast("Tasks configurated successfully!");
            }
        }
    }

    private void setWayPointActions(){
        boolean initVideo = true;
        for(String markerId: markersIds){
            String[] temp = markerId.split("-");
            int tempId = Integer.parseInt(temp[0].replace("m",""));
            TASK tempTask = TASK.valueOf(temp[1]);
            if(!waypointMissionBuilder.getWaypointList().isEmpty()){
                switch (tempTask){
                    case PICTURE: {
                        WaypointAction action = new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0);
                        waypointMissionBuilder.getWaypointList().get(tempId - 2).addAction(action);
                        break;
                    }
                    case VIDEO: {
                        if (initVideo) {
                            WaypointAction action = new WaypointAction(WaypointActionType.START_RECORD, 0);
                            waypointMissionBuilder.getWaypointList().get(tempId - 2).addAction(action);
                            initVideo = false;
                        }
                        else {
                            WaypointAction action = new WaypointAction(WaypointActionType.STOP_RECORD, 0);
                            waypointMissionBuilder.getWaypointList().get(tempId - 2).addAction(action);
                            initVideo = true;
                        }
                        break;
                    }
                    case PANORAMIC:{
                        int degrees = 60;
                        int rotation = 0;
                        for(int i = 0; i < 6; i++) {
                            rotation += degrees;
                            if(rotation > 180)
                            {
                                rotation = rotation - 360;
                            }
                            WaypointAction action1 = new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0);
                            WaypointAction action2 = new WaypointAction(WaypointActionType.ROTATE_AIRCRAFT, rotation);
                            waypointMissionBuilder.getWaypointList().get(tempId - 2).addAction(action1);
                            waypointMissionBuilder.getWaypointList().get(tempId - 2).addAction(action2);
                        }
                    }
                    case INTERVAL:{
                        waypointMissionBuilder.getWaypointList().get(tempId - 2).shootPhotoTimeInterval = 2;
                    }
                }
            }
            else {
                showToast("Waypoints could not be found.");
            }
        }
    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    private void configWayPointMission(){

        if (waypointMissionBuilder == null){

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else
        {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }

        if (waypointMissionBuilder.getWaypointList().size() > 0){

            for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++){
                //Cambio importante
                // waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
                waypointMissionBuilder.getWaypointList().get(i).altitude += altitude;
            }

            setResultToToast("Set Waypoint attitude successfully");
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    private void uploadWayPointMission(){

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

    private void startWaypointMission(){

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }

    private void stopWaypointMission(){

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            //setUpMap();
        }

        LatLng shenzhen = new LatLng(22.5362, 113.9454);
        gMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));
    }


    private void showToast(final String toastMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();

            }
        });
    }

    private void retreivePathFromDatabase(){
        //Initialize Array of pathpoints
        pathFromFirebase = new ArrayList<PathPoint>();

        //DatabaseReference ref = database.getReference("server/saving-data/fireblog/posts");
        //DatabaseReference ref = database.getReference("PATH-0/PATH");
        DatabaseReference ref = database.getReference("PATH-"+IDPATH+"/PATH");

        // Attach a listener to read the data at our posts reference
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                System.out.println("FirebaseTest2:"+dataSnapshot.getValue());
                System.out.println("FirebaseTest2: Count of Path Points: "+(int)(dataSnapshot.getChildrenCount()));



                //showToast("FirebaseTest2: Count of Path Points: "+(int)(dataSnapshot.getChildrenCount()));

                PathPoint[] path_Temp = new PathPoint[(int)dataSnapshot.getChildrenCount()];
                for (DataSnapshot userSnapshot: dataSnapshot.getChildren()) {

                    try {
                        PathPoint path_Point = userSnapshot.getValue(PathPoint.class);
                        path_Temp[path_Point.getID()]=path_Point;
                        double a = 1.0+path_Point.getZLatitude();
                    }
                    catch (Exception e)
                    {
                        showToast("Exploit111 "+e.getMessage());
                    }


                }

                pathFromFirebase = new ArrayList<PathPoint>();
                for(int i = 0;i<dataSnapshot.getChildrenCount();i++)
                {
                    pathFromFirebase.add(path_Temp[i]);
                }
                showToast("Path have been found");

                //Add path_point as way_point in map
                updatedWayPointsFromPathPoint();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.out.println("The read failed: " + databaseError.getCode());
            }
        });
    }

    private void updatedWayPointsFromPathPoint(){

        try{
            //Clear Map
            clearMap();


            waypointList = new ArrayList<>();
            pointers = new ArrayList<LatLng>();
            for(int i = 0;i<pathFromFirebase.size();i++)
            {
                Waypoint newWaypont = getWayPointFromPathPoint(pathFromFirebase.get(i));
                LatLng pointer = new LatLng(newWaypont.coordinate.getLatitude(),newWaypont.coordinate.getLongitude());
                if((i+1)!=pathFromFirebase.size()){
                    Waypoint destinyWaypont = getWayPointFromPathPoint(pathFromFirebase.get(i+1));
                    LatLng pointer2 = new LatLng(destinyWaypont.coordinate.getLatitude(),destinyWaypont.coordinate.getLongitude());
                    Polyline polyline = gMap.addPolyline(new PolylineOptions()
                            .add(pointer, pointer2)
                            .width(5)
                            .color(Color.CYAN));
                    polylines.add(polyline);
                }
                markWaypoint(pointer, i+1);
                pointers.add(pointer);
                //Add Waypoints to Waypoint arraylist;
                if (waypointMissionBuilder != null) {
                    waypointList.add(newWaypont);
                    waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                }else
                {
                    waypointMissionBuilder = new WaypointMission.Builder();
                    waypointList.add(newWaypont);
                    waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                }
            }
        }
        catch (Exception e)
        {
            showToast("Exploit 2 "+e.getMessage());
        }

    }

    private Waypoint getWayPointFromPathPoint(PathPoint point)
    {
        double new_latitude  = droneLocationLat  + (point.getZLatitude() / r_earth) * (180 / PI);
        double new_longitude = droneLocationLng + (point.getXLongitude() / r_earth) * (180 / PI) / Math.cos(droneLocationLat * PI/180);

        return new Waypoint(new_latitude,new_longitude,(float)point.getYAltitude());
    }

    private Waypoint getWayPointFromPathPointFixed(PathPoint point)
    {
        double new_latitude  = fixedDroneLocation.latitude  + (point.getZLatitude() / r_earth) * (180 / PI);
        double new_longitude = fixedDroneLocation.longitude + (point.getXLongitude() / r_earth) * (180 / PI) / Math.cos(droneLocationLat * PI/180);

        return new Waypoint(new_latitude,new_longitude,(float)point.getYAltitude());
    }

    private void clearMap(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gMap.clear();
            }

        });
        if(waypointList!=null)
            waypointList.clear();
        if(waypointMissionBuilder!=null)
            waypointMissionBuilder.waypointList(waypointList);
        updateDroneLocation();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch(item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private MediaManager.FileListStateListener updateFileListStateListener = new MediaManager.FileListStateListener() {
        @Override
        public void onFileListStateChange(MediaManager.FileListState state) {
            currentFileListState = state;
        }
    };

    private void initMediaManager() {
        if (DJIDemoApplication.getProductInstance() == null) {
            mediaFileList.clear();
            DJILog.e(TAG, "Product disconnected");
            return;
        } else {
            if (null != DJIDemoApplication.getCameraInstance() && DJIDemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                mMediaManager = DJIDemoApplication.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    DJIDemoApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            if (error == null) {
                                DJILog.e(TAG, "Set cameraMode success");

                                getFileList();
                            } else {
                                setResultToToast("Set cameraMode failed");
                            }
                        }
                    });
                    if (mMediaManager.isVideoPlaybackSupported()) {
                        DJILog.e(TAG, "Camera support video playback!");
                    } else {
                        setResultToToast("Camera does not support video playback!");
                    }
                    scheduler = mMediaManager.getScheduler();
                }
            } else if (null != DJIDemoApplication.getCameraInstance()
                    && !DJIDemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                setResultToToast("Media Download Mode not Supported");
            }
        }
        return;
    }

    private void getFileList() {
        mMediaManager = DJIDemoApplication.getCameraInstance().getMediaManager();
        if (mMediaManager != null) {

            if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)){
                DJILog.e(TAG, "Media Manager is busy.");
            }else{

                mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, new CommonCallbacks.CompletionCallback() {

                    @Override
                    public void onResult(DJIError djiError) {
                        if (null == djiError) {


                            //Reset data
                            if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                                mediaFileList.clear();

                            }

                            mediaFileList = mMediaManager.getSDCardFileListSnapshot();
                            Collections.sort(mediaFileList, new Comparator<MediaFile>() {
                                @Override
                                public int compare(MediaFile lhs, MediaFile rhs) {
                                    if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                        return 1;
                                    } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                        return -1;
                                    }
                                    return 0;
                                }
                            });
                            scheduler.resume(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {
                                    if (error == null) {

                                    }
                                }
                            });
                        } else {

                            setResultToToast("Get Media File List Failed:" + djiError.getDescription());
                        }
                    }
                });
            }
        }
    }

    private FetchMediaTask.Callback taskCallback = new FetchMediaTask.Callback() {
        @Override
        public void onUpdate(MediaFile file, FetchMediaTaskContent option, DJIError error) {
            if (null == error) {
                if (option == FetchMediaTaskContent.PREVIEW) {
                    runOnUiThread(new Runnable() {
                        public void run() {

                        }
                    });
                }
                if (option == FetchMediaTaskContent.THUMBNAIL) {
                    runOnUiThread(new Runnable() {
                        public void run() {

                        }
                    });
                }
            } else {
                DJILog.e(TAG, "Fetch Media Task Failed" + error.getDescription());
            }
        }
    };

    private void downloadFileByIndex(){
        for(int i = 0; i < mediaFileList.size(); i++) {
            mediaFileList.get(i).fetchFileData(destDir, null, new DownloadListener<String>() {
                @Override
                public void onFailure(DJIError error) {

                    setResultToToast("Download File Failed" + error.getDescription());

                }

                @Override
                public void onProgress(long total, long current) {
                }

                @Override
                public void onRateUpdate(long total, long current, long persize) {
                    int tmpProgress = (int) (1.0 * current / total * 100);

                }

                @Override
                public void onStart() {
                    showToast("Iniciamos...");
                }

                @Override
                public void onSuccess(String filePath) {

                    setResultToToast("Download File Success" + ":" + filePath);

                }
            });
        }
    }
}
