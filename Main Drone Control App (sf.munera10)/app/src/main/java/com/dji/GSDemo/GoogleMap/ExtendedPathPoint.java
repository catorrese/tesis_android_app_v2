package com.dji.GSDemo.GoogleMap;

public class ExtendedPathPoint extends PathPoint{
    private  String instruction;
    private int task;

    public ExtendedPathPoint(double YAltitude, double ZLatitude, double XLongitude, int ID, String instruction, int task){
        super(YAltitude, ZLatitude, XLongitude, ID);
        this.instruction = instruction;
        this.task = task;
    }

    public ExtendedPathPoint(){
        super();
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public int getTask() {
        return task;
    }

    public void setTask(int task) {
        this.task = task;
    }
}
