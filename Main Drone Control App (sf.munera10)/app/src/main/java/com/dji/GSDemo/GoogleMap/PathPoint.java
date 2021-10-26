package com.dji.GSDemo.GoogleMap;

public class PathPoint {

    protected  double YAltitude;
    protected  double ZLatitude;
    protected  double XLongitude;
    protected  int ID;

    public PathPoint(double YAltitude, double ZLatitude, double XLongitude, int ID){
        this.YAltitude = YAltitude;
        this.ZLatitude = ZLatitude;
        this.XLongitude = XLongitude;
        this.ID = ID;
    }

    public PathPoint(){}

    public double getZLatitude() {
        return ZLatitude;
    }

    public void setZLatitude(double ZLatitude) {
        this.ZLatitude = ZLatitude;
    }





    public double getYAltitude() {
        return YAltitude;
    }

    public void setYAltitude(double YAltitude) {
        this.YAltitude = YAltitude;
    }

    public double getXLongitude() {
        return XLongitude;
    }

    public void setXLongitude(double XLongitude) {
        this.XLongitude = XLongitude;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }
}
