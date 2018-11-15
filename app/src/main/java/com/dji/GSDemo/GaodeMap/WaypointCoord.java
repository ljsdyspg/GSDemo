package com.dji.GSDemo.GaodeMap;

import java.util.ArrayList;

public class WaypointCoord {
    static int size = 100;
    static float altitude = 3.0f;
    static float speed = 3.0f;
    static double[] coord= new double[size];
    static ArrayList coords = new ArrayList();


    public static void initWayPoint() {
        double[] temp;
        coords.clear();
        coords.add(new double[]{30.5309412900,114.3563523600});
        coords.add(new double[]{30.5309542900,114.3551733600});
        coords.add(new double[]{30.5291012900,114.3551673600});
        coords.add(new double[]{30.5290782900,114.3565893600});
        coords.add(new double[]{30.5290872900,114.3579843600});
        coords.add(new double[]{30.5309452900,114.3580373600});
        coords.add(new double[]{30.5309612500,114.3598595400});
        coords.add(new double[]{30.5292842500,114.3598535400});
    }
}
