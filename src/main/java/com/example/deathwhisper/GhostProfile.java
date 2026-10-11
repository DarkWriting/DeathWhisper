package com.example.deathwhisper;

import java.util.Locale;

/** Six original investigative archetypes. Each has a distinct pair of evidence types. */
public enum GhostProfile {
    WAILER(1|2,"The Wailer","Whispering and unstable electromagnetic readings"),
    PALE_WALKER(1|4,"The Pale Walker","Cold corridors and sudden device surges"),
    INKBOUND(2|8,"The Inkbound","Voices trapped in paintings and stained footprints"),
    HOLLOW(4|8,"The Hollow","Freezing air and impossible tracks"),
    WITNESS(1|8,"The Witness","Electromagnetic disturbances and living paintings"),
    MOURNER(2|4,"The Mourner","Disembodied voices and bitter cold");

    public static final int EMF=1, VOICE=2, FROST=4, INK=8;
    public final int evidence;
    public final String description;
    public final String clue;
    GhostProfile(int evidence,String description,String clue){
        this.evidence=evidence;this.description=description;this.clue=clue;
    }
    public boolean has(int flag){return (evidence & flag)!=0;}
    public static GhostProfile parse(String value){
        try{return valueOf(value.toUpperCase(Locale.ROOT));}catch(Exception e){return null;}
    }
    public static String evidenceText(int flags){
        java.util.List<String> found=new java.util.ArrayList<>();
        if((flags&EMF)!=0)found.add("EMF spike");
        if((flags&VOICE)!=0)found.add("Spirit voice");
        if((flags&FROST)!=0)found.add("Freezing air");
        if((flags&INK)!=0)found.add("Spectral ink");
        return found.isEmpty()?"no evidence yet":String.join(", ",found);
    }
}
