package com.vg.restrictacesscontrol.models;

public class Car {
    public int id;
    public String lic_no;
    public String name;
    public String thumb;      // file name
    public Integer seats;
    public Integer is_rental;
    public String etc_code;
    public Integer responsible_driver_id;
    public String live_id;
    public String created_at;
    public String updated_at;

    public String getThumbUrl() {
        return "http://gmo021.cansportsvg.com/api/storage/app/vg-transports/car_thumbnail/" + thumb;
    }
}

