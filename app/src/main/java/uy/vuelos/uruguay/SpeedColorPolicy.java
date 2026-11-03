package uy.vuelos.uruguay;

/** Display policy only. Never infer a legal limit or grant tolerance under traffic law. */
public final class SpeedColorPolicy {
    public static final int UNKNOWN=0, NORMAL=1, AMBER=2, RED=3;
    // The user-facing visual preference: 1..6 km/h over recorded limit = amber; +7 or more = red.
    public static int state(int measuredKmh,int recordedLimitKmh){
        if(measuredKmh<0 || recordedLimitKmh<=0)return UNKNOWN;
        int excess=measuredKmh-recordedLimitKmh;
        if(excess<=0)return NORMAL;
        if(excess<7)return AMBER;
        return RED;
    }
    public static int excess(int measuredKmh,int recordedLimitKmh){
        return (measuredKmh<0||recordedLimitKmh<=0)?0:
            Math.max(0,measuredKmh-recordedLimitKmh);
    }
    private SpeedColorPolicy(){}
}
