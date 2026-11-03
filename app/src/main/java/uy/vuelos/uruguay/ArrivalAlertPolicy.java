package uy.vuelos.uruguay;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure timing policy for cached arrivals: no GPS, network calls or push service. */
public final class ArrivalAlertPolicy {
    public static final long MINUTE=60000L;
    public static final long BAND=15*MINUTE;
    public static final int PREPARE=40, DEPART=20, LANDED=0;
    public static final int MAX_GROUPS=6;
    public static final long MAX_AGE=75*MINUTE;
    public static final long PREPARE_MIN=30*MINUTE;
    public static final long ALERT_MIN=4*MINUTE;
    public static final long MAX_HORIZON=80*MINUTE;

    public static final class Alert {
        public final int stage;
        public final String key;
        public final long deliveryAt,arrivalAt;
        public final String origin, flightNumber;
        public final int flights;
        Alert(int s,String k,long d,long a,String o,String n,int count){
            stage=s;key=k;deliveryAt=d;arrivalAt=a;origin=o;flightNumber=n;flights=count;
        }
    }
    private static final class Group {
        final ArrayList<FlightData.Flight> flights=new ArrayList<>();
        long eta=Long.MAX_VALUE;
        FlightData.Flight lead;
        void add(FlightData.Flight f){
            flights.add(f);
            if(f.landingTime()<eta){eta=f.landingTime();lead=f;}
        }
    }
    public static List<Alert> plan(List<FlightData.Flight> flights,long now){
        ArrayList<Alert> out=new ArrayList<>();
        if(flights==null || flights.isEmpty())return out;
        LocalDate today=Instant.ofEpochMilli(now).atZone(FlightData.UY).toLocalDate();
        java.util.TreeMap<Long,Group> grouped=new java.util.TreeMap<>();
        for(FlightData.Flight f:flights){
            if(f.canceled || f.landed || f.scheduledMillis<=0)continue;
            if(!today.equals(f.day()))continue;
            long remaining=f.landingTime()-now;
            if(remaining<ALERT_MIN || remaining>MAX_HORIZON)continue;
            long band=f.landingTime()/BAND;
            Group g=grouped.get(band);
            if(g==null){g=new Group();grouped.put(band,g);}
            g.add(f);
        }
        int count=0;
        for(Map.Entry<Long,Group> entry:grouped.entrySet()){
            if(count++>=MAX_GROUPS)break;
            Group group=entry.getValue();
            long eta=group.eta;
            long remaining=eta-now;
            String groupId=String.valueOf(entry.getKey());
            if(remaining>=PREPARE_MIN){
                out.add(new Alert(PREPARE,groupId+"_40",
                    Math.max(now+3000L,eta-40*MINUTE),eta,group.lead.origin,
                    group.lead.flightNo,group.flights.size()));
            }
            if(remaining>=ALERT_MIN){
                out.add(new Alert(DEPART,groupId+"_20",
                    Math.max(now+7000L,eta-20*MINUTE),eta,group.lead.origin,
                    group.lead.flightNo,group.flights.size()));
            }
        }
        return out;
    }
    public static boolean validDelivery(int stage,long eta,long fetched,long now){
        if(fetched<=0 || now<fetched-60000L || now-fetched>MAX_AGE)return false;
        long remaining=eta-now;
        if(stage==PREPARE)return remaining>=25*MINUTE && remaining<=MAX_HORIZON;
        if(stage==DEPART)return remaining>=ALERT_MIN && remaining<=35*MINUTE;
        return false;
    }
    public static String remaining(long eta,long now){
        if(eta<=now)return "horario vencido";
        long minutes=(eta-now+MINUTE-1)/MINUTE;
        return minutes<60 ? minutes+" min" : minutes/60+" h "+minutes%60+" min";
    }
    private ArrivalAlertPolicy(){}
}
