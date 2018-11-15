package com.dji.GSDemo.GaodeMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.CoordinateConverter;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.api.maps2d.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class MyWaypointMission extends FragmentActivity implements View.OnClickListener{

    protected static final String TAG = "MyWaypointMission";

    private MapView mapView;
    private AMap aMap;

    private Button locate, test, fpv, start, stop, resume, pause,clear;

    private int pointNum = 0;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;
    private float maxSpeed = 10.0f;

    private double droneLocationLat = 181, droneLocationLng = 181;//181超出180的范围，所以设置该初值
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;//表示飞机位置的标记对象

    private List<Waypoint> waypointList = new ArrayList<>();//存储路径，Wayponit三个参数，经纬高
    private List<LatLng> mapPointList = new ArrayList<>();//存储地图上的点，用来画线

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause(){
        super.onPause();
        resumeWaypointMission();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        MyWaypointMission.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MyWaypointMission.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI(){
        locate = (Button) findViewById(R.id.locate);
        test = (Button) findViewById(R.id.test);
        fpv = (Button) findViewById(R.id.fpv);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        resume = (Button) findViewById(R.id.resume);
        pause = (Button) findViewById(R.id.pause);
        clear = (Button) findViewById(R.id.clear);

        locate.setOnClickListener(this);
        test.setOnClickListener(this);
        fpv.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        resume.setOnClickListener(this);
        pause.setOnClickListener(this);
        clear.setOnClickListener(this);
    }

    private void initMapView() {

        if (aMap == null) {
            aMap = mapView.getMap();
        }

        LatLng WHU = new LatLng(30.5304782900, 114.3555023600);
        aMap.addMarker(new MarkerOptions().position(WHU).title("Marker in WHU"));//添加标记
        aMap.moveCamera(CameraUpdateFactory.newLatLng(WHU));//标记视野居中嘛
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint_mission);


        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);//过滤器监视飞机连接状态的改变
        registerReceiver(mReceiver, filter);//注册广播接收器

        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);

        initMapView();
        initUI();
        addListener();
    }

    //为了感知飞机的连接状态，这里注册一个广播接收器，当飞机的连接状态改变时，onReceive()即被调用
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
    //这里再次登录账号
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

        BaseProduct product = DJIDemoApplication.getProductInstance();//获取product实例
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {//Aircraft继承自BaseProduct
                mFlightController = ((Aircraft) product).getFlightController();//当product是Aircraft的实例时，获取FlightController的实例
            }
        }

        if (mFlightController != null) {
            //这里通过FlightController获取飞机的经纬度数据
            mFlightController.setStateCallback(
                    new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(FlightControllerState
                                                     djiFlightControllerCurrentState) {
                            droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                            droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                            updateDroneLocation();
                        }
                    });

        }
    }
    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {//getWaypointMissionOperator()返回WaypointMissionOperator实例，可执行
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

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }
        return instance;
    }

    //检查经纬度数值的合法性，返回布尔值
    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void drawLine(LatLng point){
        mapPointList.add(point);
        if (mapPointList.size()>1){
            aMap.addPolyline(new PolylineOptions().
                    addAll(mapPointList).width(10).color(Color.YELLOW));
        }
    }

    private void updateDroneLocation(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //显示飞机时，GPS坐标转换为火星坐标
        pos = WG2GCJ(pos);
        //创建一个地图上的标记用来表示当前飞机的位置，MarkerOptions为可定义的Marker选项
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);//设置位置
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));//设置图标，通过BitmapDescriptorFactory获取一个BitmapDescriptor对象
        //当updateDroneLocation()被调用时，飞机的位置发生改变，所以还需要更新UI显示飞机的位置
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = aMap.addMarker(markerOptions);//经纬度合法，添加进地图中
                }
            }
        });
    }

    private LatLng WG2GCJ(LatLng latLng){
        CoordinateConverter converter = new CoordinateConverter();
        converter.from(CoordinateConverter.CoordType.GPS);
        converter.coord(latLng);
        return converter.convert();
    }

    private void markWaypoint(LatLng point){
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        int pic_i = R.drawable.pic_1;
        switch (pointNum){
            case 1 :pic_i=R.drawable.pic_1;break;
            case 2 :pic_i=R.drawable.pic_2;break;
            case 3 :pic_i=R.drawable.pic_3;break;
            case 4 :pic_i=R.drawable.pic_4;break;
            case 5 :pic_i=R.drawable.pic_5;break;
            case 6 :pic_i=R.drawable.pic_6;break;
            case 7 :pic_i=R.drawable.pic_7;break;
            case 8 :pic_i=R.drawable.pic_8;break;
            case 9 :pic_i=R.drawable.pic_9;break;
            default:pic_i=R.drawable.pic_1;break;
        }
        markerOptions.icon(BitmapDescriptorFactory.fromResource(pic_i));
        Marker marker = aMap.addMarker(markerOptions);
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
            case R.id.clear: {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aMap.clear();
                    }
                });//把地图上的覆盖物都清除掉
                waypointList.clear();//清除已设置的路径
                mapPointList.clear();//清楚已画出的路线
                pointNum=0;
                waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();//然后再把飞机的位置显示出来
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
            case R.id.test:{
                myWaypointMisson();
                break;
            }
            case R.id.fpv:{
                startActivity(new Intent(this,FPVActivity.class));
            }
            case R.id.pause:{
                pauseWaypointMission();
                break;
            }
            case R.id.resume:{
                resumeWaypointMission();
                break;
            }
            default:
                break;
        }
    }

    //按下location,视角切换到以飞机的位置为居中位置
    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        pos = WG2GCJ(pos);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        aMap.moveCamera(cu);
    }

    //将对话框中所设定的飞行参数导入waypointMissionBuilder中
    private void configWayPointMission(){
        //mFinishedAction mHeadingMode mSpeed mSpeed
        if (waypointMissionBuilder == null){

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(maxSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else
        {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(maxSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }

        if (waypointMissionBuilder.getWaypointList().size() > 0){
            //每段航程的高度都设置成一样的
            for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++){
                waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
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

    //上传路径飞行任务到飞机
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
    //开始执行路径飞行任务
    private void startWaypointMission(){

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }
    //停止执行路径飞行任务
    private void stopWaypointMission(){

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }
    private void pauseWaypointMission(){

        getWaypointMissionOperator().pauseMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Pause: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }
    private void resumeWaypointMission(){

        getWaypointMissionOperator().resumeMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Resume: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }


    private void myWaypointMisson(){
        double temp[];
        ArrayList coords = new ArrayList();
        LatLng point;

        WaypointCoord.initWayPoint();
        coords = WaypointCoord.coords;
        altitude = WaypointCoord.altitude;
        mSpeed = WaypointCoord.speed;
        maxSpeed = WaypointCoord.speed;

        waypointMissionBuilder = new WaypointMission.Builder();
        waypointList.clear();

        for (int i = 0; i < coords.size(); i++) {
            temp = (double[])coords.get(i);
            point = WG2GCJ(new LatLng(temp[0],temp[1]));

            pointNum++;
            markWaypoint(point);
            drawLine(point);

            Waypoint mWaypoint = new Waypoint(temp[0],temp[1],altitude);
            waypointList.add(mWaypoint);
        }
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());

        configWayPointMission();
        uploadWayPointMission();
    }
}

