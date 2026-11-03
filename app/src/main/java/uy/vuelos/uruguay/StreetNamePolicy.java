package uy.vuelos.uruguay;

/** Filters geocoder street names; never upgrades a neighborhood or a house number to a street. */
public final class StreetNamePolicy {
    public static String clean(String text){
        if(text==null)return "";
        String result=text.replaceAll("[\\p{Cntrl}]"," ").trim().replaceAll("\\s+"," ");
        if(result.isEmpty()||result.matches("[0-9\\s.,#-]+"))return "";
        if(result.length()>70)result=result.substring(0,70).trim();
        return result;
    }
    private StreetNamePolicy(){}
}
