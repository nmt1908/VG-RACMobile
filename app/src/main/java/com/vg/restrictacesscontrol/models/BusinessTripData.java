package com.vg.restrictacesscontrol.models;

public class BusinessTripData {
    public Integer id;
    public String start_date;
    public String employee_list;
    public String check_qr;
    public BusinessTripData data;

    public BusinessTripData(Integer id, String start_date, String employee_list, BusinessTripData data) {
        this.id = id;
        this.start_date = start_date;
        this.employee_list = employee_list;
        this.data = data;
    }
}
