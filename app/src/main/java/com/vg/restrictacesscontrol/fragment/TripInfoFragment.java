package com.vg.restrictacesscontrol.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.activitys.FaceRecognizeActivity;
import com.vg.restrictacesscontrol.adapter.EmployeeAdapter;
import com.vg.restrictacesscontrol.models.BusinessTripData;
import com.vg.restrictacesscontrol.models.BusinessTripResponse;
import com.vg.restrictacesscontrol.models.CheckReq;
import com.vg.restrictacesscontrol.models.Employee;
import com.vg.restrictacesscontrol.service.EmpService;
import com.vg.restrictacesscontrol.service.VgBtaService;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TripInfoFragment extends Fragment {
    private static final String TAG_TRIP  = "BTA_TRIP";
    private static final String TAG_HRM   = "BTA_HRM";
    private static final String TAG_CACHE = "BTA_CACHE";
    private static final String TAG_READY = "BTA_READY";

    private FloatingActionButton fabFaceId;
    private TextView tvId, tvDate, tvCount;
    private ProgressBar progress;
    private RecyclerView recycler;
    private EmployeeAdapter adapter;

    private final Gson gson = new Gson();
    private final Type EMP_LIST_TYPE = new TypeToken<List<Employee>>() {}.getType();

    // “Có màu”:
    private final Set<String> verifiedAccum = new HashSet<>(); // xanh: empno match
    private final List<Employee> extraPeople = new ArrayList<>(); // đỏ: người ngoài list
    private final Set<String> extraEmpnos = new HashSet<>();     // chặn trùng người đỏ theo empno

    // Để recache sau FaceID
    private Integer lastTripId = null;
    private String  lastQrCode = "";
    private String  lastRawEmpList = "[]";

    // Trạng thái IN/OUT giống Vue (mặc định "out" nếu chưa có lịch sử)
    private String statusInOut = "out";

    // HRM service
    private EmpService empService;
    private final Set<String> hrmFetching = new HashSet<>(); // tránh enqueue trùng

    private final ActivityResultLauncher<Intent> faceAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (getActivity() == null) return;
                if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                    ArrayList<String> verifiedJsonList = result.getData().getStringArrayListExtra("verified_people");
                    Log.d(TAG_HRM, "FaceID result -> size=" + (verifiedJsonList == null ? 0 : verifiedJsonList.size()));
                    if (verifiedJsonList != null && !verifiedJsonList.isEmpty()) {
                        adapter.enableVerificationColors(true);

                        List<Employee> newPeople = new ArrayList<>();

                        for (String jsonStr : verifiedJsonList) {
                            try {
                                JSONObject obj = new JSONObject(jsonStr);
                                String cardId = obj.optString("cardId");
                                String name   = obj.optString("name");
                                String photo  = obj.optString("photo");

                                Log.d(TAG_HRM, "FaceID person -> empno=" + cardId + ", name=" + name);

                                if (cardId != null && !cardId.isEmpty()) {
                                    verifiedAccum.add(cardId);

                                    boolean exists = false;
                                    for (Employee e : adapter.getItems()) {
                                        if (cardId.equals(e.empno)) { exists = true; break; }
                                    }
                                    // người lạ -> thêm 1 lần duy nhất, đồng thời fetch HRM
                                    if (!exists && extraEmpnos.add(cardId)) {
                                        Log.d(TAG_HRM, "Extra detected (red). Will add & fetch HRM -> empno=" + cardId);
                                        Employee newEmp = new Employee();
                                        newEmp.empno = cardId;
                                        newEmp.name  = (name != null && !name.isEmpty()) ? name : "(Unknown)";
                                        newEmp.photo = photo;
                                        extraPeople.add(newEmp);
                                        newPeople.add(newEmp);

                                        // gọi HRM để điền dept/sex và cache
                                        fetchAndCacheHrm(cardId);
                                    } else if (exists) {
                                        Log.d(TAG_HRM, "FaceID in-list -> mark green only, empno=" + cardId);
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e(TAG_HRM, "Parse face result error: " + ex.getMessage());
                            }
                        }

                        if (!newPeople.isEmpty()) {
                            adapter.addOrUpdatePeople(newPeople);
                            Log.d(TAG_READY, "UI appended " + newPeople.size() + " extra(s)");
                        }
                        adapter.setVerified(verifiedAccum);
                        adapter.setOnExtraRemoveListener(this::onRemoveExtraRequested);
                        Log.d(TAG_READY, "VerifiedAccum size=" + verifiedAccum.size());

                        // Rebuild & recache ngay (tạm thời, sẽ recache lần nữa khi HRM trả về)
                        cacheTripForPayload(lastTripId, lastQrCode, lastRawEmpList);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_infomation_trip, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvId = view.findViewById(R.id.tvId);
        tvDate = view.findViewById(R.id.tvDate);
        tvCount = view.findViewById(R.id.tvCount);
        progress = view.findViewById(R.id.progress);
        recycler = view.findViewById(R.id.recycler);
        fabFaceId = view.findViewById(R.id.fabFaceId);

        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        adapter = new EmployeeAdapter();
        recycler.setAdapter(adapter);
        adapter.enableVerificationColors(false);
        adapter.setOnExtraRemoveListener(this::onRemoveExtraRequested); // gắn listener từ đầu

        String raw = getArguments() != null ? getArguments().getString("qr_raw", "") : "";
        lastQrCode = raw != null ? raw : "";
        fetchTrip(lastQrCode);

        fabFaceId.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), FaceRecognizeActivity.class);
            faceAuthLauncher.launch(i);
        });
    }

    private void fetchTrip(String qrRaw) {
        progress.setVisibility(View.VISIBLE);

        HttpLoggingInterceptor log = new HttpLoggingInterceptor(message -> Log.d("HTTP", message));
        log.setLevel(HttpLoggingInterceptor.Level.BODY); // DEBUG kỹ network
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(log).build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://gmo021.cansportsvg.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        VgBtaService svc = retrofit.create(VgBtaService.class);
        empService = retrofit.create(EmpService.class); // HRM

        Log.d(TAG_TRIP, "Call /checkQrCodeV3, qr=" + qrRaw);
        svc.checkQrCodeV3(new CheckReq(qrRaw)).enqueue(new Callback<BusinessTripResponse>() {
            @Override
            public void onResponse(Call<BusinessTripResponse> call, Response<BusinessTripResponse> resp) {
                progress.setVisibility(View.GONE);
                Log.d(TAG_TRIP, "/checkQrCodeV3 -> code=" + resp.code());
                if (!resp.isSuccessful() || resp.body() == null) {
                    toast("Không có dữ liệu");
                    Log.w(TAG_TRIP, "HTTP " + resp.code() + " hoặc body null");
                    return;
                }

                BusinessTripResponse body = resp.body();

                // Ưu tiên dùng data; nếu null thì fallback top-level
                BusinessTripData node = (body.data != null)
                        ? body.data
                        : new BusinessTripData(body.id, body.start_date, body.employee_list, null);

                Integer usedId        = (node != null && node.id != null) ? node.id : body.id;
                String  usedStartDate = (node != null && node.start_date != null) ? node.start_date : body.start_date;
                String  rawEmpList    = (node != null && node.employee_list != null) ? node.employee_list : body.employee_list;

                if (usedId == null) {
                    toast("Không tìm thấy chuyến đi");
                    Log.w(TAG_TRIP, "usedId == null");
                    return;
                }

                // === TÍNH TRẠNG THÁI IN/OUT GIỐNG VUE ===
                statusInOut = resolveNextStatusFromResponse(node, body);
                Log.d(TAG_TRIP, "statusInOut (next action) = " + statusInOut);

                // Lưu lại để recache sau FaceID
                lastTripId     = usedId;
                lastRawEmpList = rawEmpList != null ? rawEmpList : "[]";

                // Parse danh sách nhân sự
                List<Employee> employees;
                try {
                    employees = gson.fromJson(rawEmpList, EMP_LIST_TYPE);
                    if (employees == null) employees = Collections.emptyList();
                } catch (Exception e) {
                    employees = Collections.emptyList();
                    Log.e(TAG_TRIP, "Parse employee_list error: " + e.getMessage());
                }

                String start = (usedStartDate != null) ? usedStartDate : "-";
                int count    = (employees != null) ? employees.size() : 0;
                tvId.setText(getString(R.string.id) + usedId);
                tvDate.setText(getString(R.string.departure_date) + start);
                tvCount.setText(getString(R.string.quantity)+ count);

                adapter.submit(employees);   // hoặc Collections.emptyList() nếu null


                // LOG raw & snapshot
                try { Log.d(TAG_TRIP, "=== /checkQrCodeV3 RAW BODY ===\n" + gson.toJson(body)); } catch (Exception ignore) {}
                try {
                    JSONObject snap = new JSONObject();
                    snap.put("id", usedId);
                    snap.put("start_date", usedStartDate != null ? usedStartDate : JSONObject.NULL);
                    snap.put("employee_count", employees.size());
                    snap.put("status_inout_next", statusInOut);
                    if (rawEmpList != null && !rawEmpList.trim().isEmpty()) {
                        try { snap.put("employee_list", new JSONArray(rawEmpList)); }
                        catch (JSONException jx) { snap.put("employee_list_raw", rawEmpList); }
                    }
                    Log.d(TAG_TRIP, "=== TRIP SNAPSHOT (used fields) ===\n" + snap.toString(2));
                } catch (Exception e) {
                    Log.e(TAG_TRIP, "Snapshot log error: " + e.getMessage());
                }

                Log.d(TAG_TRIP, "UI bound -> id=" + usedId +
                        ", start_date=" + (usedStartDate != null ? usedStartDate : "") +
                        ", employees=" + employees.size());

                // LƯU CACHE (kèm status_inout) cho các màn sau dùng
                cacheTripForPayload(usedId, lastQrCode, lastRawEmpList);

                // Giữ lại màu xác thực/nối thêm người nếu trước đó đã có từ FaceID
                if (!extraPeople.isEmpty()) {
                    adapter.addOrUpdatePeople(extraPeople);
                    Log.d(TAG_READY, "Re-append saved extras to UI, size=" + extraPeople.size());
                }
                if (!verifiedAccum.isEmpty()) {
                    adapter.enableVerificationColors(true);
                    adapter.setVerified(verifiedAccum);
                    adapter.setOnExtraRemoveListener(TripInfoFragment.this::onRemoveExtraRequested);
                    Log.d(TAG_READY, "Re-apply verifiedAccum, size=" + verifiedAccum.size());
                }
            }

            @Override
            public void onFailure(Call<BusinessTripResponse> call, Throwable t) {
                progress.setVisibility(View.GONE);
                Log.e(TAG_TRIP, "/checkQrCodeV3 failure: " + t.getMessage(), t);
                toast("Lỗi tải dữ liệu");
            }
        });
    }

    /** Lấy check_qr từ node/body và suy ra trạng thái kế tiếp (giống Vue) */
    private String resolveNextStatusFromResponse(BusinessTripData node, BusinessTripResponse body) {
        String checkQrRaw = null;

        // 1) Ưu tiên field đã map sẵn
        if (node != null && node.check_qr != null && !node.check_qr.trim().isEmpty()) {
            checkQrRaw = node.check_qr;
            Log.d(TAG_TRIP, "resolveNextStatusFromResponse -> from node.check_qr (len=" + node.check_qr.length() + ")");
        } else if (body != null && body.check_qr != null && !body.check_qr.trim().isEmpty()) {
            checkQrRaw = body.check_qr;
            Log.d(TAG_TRIP, "resolveNextStatusFromResponse -> from body.check_qr (len=" + body.check_qr.length() + ")");
        }

        // 2) Fallback: parse from gson json nếu model chưa có field
        if (checkQrRaw == null || checkQrRaw.trim().isEmpty()) {
            try {
                JSONObject bodyJson = new JSONObject(gson.toJson(body));
                if (bodyJson.has("data")) {
                    JSONObject dataJson = bodyJson.optJSONObject("data");
                    if (dataJson != null) {
                        String t = dataJson.optString("check_qr", null);
                        if (t != null && !t.trim().isEmpty()) checkQrRaw = t;
                    }
                }
                if (checkQrRaw == null || checkQrRaw.trim().isEmpty()) {
                    String t2 = bodyJson.optString("check_qr", null);
                    if (t2 != null && !t2.trim().isEmpty()) checkQrRaw = t2;
                }
            } catch (Exception e) {
                Log.w(TAG_TRIP, "resolveNextStatusFromResponse parse err: " + e.getMessage());
            }
        }

        Log.d(TAG_TRIP, "resolveNextStatusFromResponse -> check_qr(raw)=" + (checkQrRaw == null ? "null" : checkQrRaw));

        if (checkQrRaw == null || checkQrRaw.trim().isEmpty()) {
            Log.d(TAG_TRIP, "No history -> next='out'");
            return "out";
        }

        try {
            JSONArray arr = new JSONArray(checkQrRaw);
            Log.d(TAG_TRIP, "check_qr array size=" + arr.length());
            if (arr.length() == 0) return "out";
            JSONObject last = arr.optJSONObject(arr.length() - 1);
            String lastStatus = (last != null) ? last.optString("status", "") : "";
            return "in".equalsIgnoreCase(lastStatus) ? "out" : "in";
        } catch (Exception e) {
            Log.w(TAG_TRIP, "resolveNextStatusFromResponse array err: " + e.getMessage());
            return "out";
        }
    }

    /** HRM cache helpers */
    private void putHrmCache(String empno, String dept, String sex, String name) {
        try {
            JSONObject o = new JSONObject();
            o.put("dept", dept != null ? dept : "");
            o.put("sex",  sex  != null ? sex  : "");
            if (name != null) o.put("name", name);

            SharedPreferences sp = requireContext()
                    .getSharedPreferences("hrm_cache", android.content.Context.MODE_PRIVATE);
            sp.edit().putString(empno, o.toString()).apply();
            Log.d(TAG_CACHE, "HRM cache put -> empno=" + empno + ", dept=" + dept + ", sex=" + sex + ", name=" + name);
        } catch (Exception ex) {
            Log.e(TAG_CACHE, "HRM cache put error: " + ex.getMessage());
        }
    }

    private JSONObject readHrmCache(String empno) {
        try {
            SharedPreferences sp = requireContext()
                    .getSharedPreferences("hrm_cache", android.content.Context.MODE_PRIVATE);
            String s = sp.getString(empno, null);
            if (s != null) {
                Log.d(TAG_CACHE, "HRM cache hit -> empno=" + empno + ", json=" + s);
                return new JSONObject(s);
            }
            Log.d(TAG_CACHE, "HRM cache miss -> empno=" + empno);
            return null;
        } catch (Exception e) {
            Log.e(TAG_CACHE, "HRM cache read error: " + e.getMessage());
            return null;
        }
    }

    private void fetchAndCacheHrm(String empno) {
        if (empService == null || empno == null || empno.trim().isEmpty()) {
            Log.w(TAG_HRM, "fetchAndCacheHrm skip (service null or empno empty)");
            return;
        }
        if (!hrmFetching.add(empno)) {
            Log.d(TAG_HRM, "HRM already in-flight -> empno=" + empno);
            return;
        }
        Log.d(TAG_HRM, "Enqueue HRM GET /api/emp/getByEmpNoHRMMobile/" + empno);
        empService.getByEmpNoHRM(empno.trim()).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> resp) {
                hrmFetching.remove(empno);
                int code = resp.code();
                String bodyStr = null;
                try { bodyStr = (resp.body() != null) ? resp.body().string() : null; } catch (Exception ignored) {}
                Log.d(TAG_HRM, "HRM response -> code=" + code + ", body.len=" + (bodyStr == null ? 0 : bodyStr.length()));
                try {
                    if (!resp.isSuccessful() || bodyStr == null) {
                        Log.w(TAG_HRM, "HRM failed or empty body -> empno=" + empno);
                        return;
                    }
                    JSONObject obj = new JSONObject(bodyStr);

                    String dept = obj.optString("dept", "");
                    if (dept.isEmpty()) dept = obj.optString("dept_code", "");
                    if (dept.isEmpty()) dept = obj.optString("dept_name", "");

                    String sex  = obj.optString("sex", "");
                    if (sex.isEmpty()) sex = obj.optString("gender", "");
                    if (sex.isEmpty()) sex = obj.optString("gioi_tinh", "");
                    if (sex.isEmpty()) sex = "Không rõ";

                    String name = obj.optString("name", "");

                    Log.d(TAG_HRM, "Parsed HRM -> empno=" + empno + ", dept=" + dept + ", sex=" + sex + ", name=" + name);

                    putHrmCache(empno, dept, sex, name);

                    // Rebuild ready[] để payload lấy được dept/sex ngay lần sau
                    cacheTripForPayload(lastTripId, lastQrCode, lastRawEmpList);
                } catch (Exception ex) {
                    Log.e(TAG_HRM, "HRM parse/cache error: " + ex.getMessage(), ex);
                }
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) {
                hrmFetching.remove(empno);
                Log.e(TAG_HRM, "HRM failure -> empno=" + empno + ", err=" + t.getMessage(), t);
            }
        });
    }

    /** Chuẩn hoá employee_list thành chuỗi JSONArray hợp lệ trước khi lưu cache */
    private String normalizeEmployeeList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "[]";
        try {
            JSONArray arr = new JSONArray(raw);
            return arr.toString(); // nén gọn
        } catch (JSONException e) {
            Log.w(TAG_TRIP, "employee_list không phải JSONArray hợp lệ, lưu dạng raw");
            return raw;
        }
    }

    /** Xây mảng ready (đã gán checked + append người lạ, fill dept/sex từ HRM cache nếu có) */
    private JSONArray buildEmployeeJSONArrayWithChecked(String rawEmpList) {
        JSONArray out = new JSONArray();
        final Set<String> inListEmpnos = new HashSet<>();
        try {
            JSONArray src = new JSONArray(normalizeEmployeeList(rawEmpList));
            Log.d(TAG_READY, "Build ready[] from raw.size=" + src.length() + ", verified.size=" + verifiedAccum.size() + ", extras.size=" + extraPeople.size());
            for (int i = 0; i < src.length(); i++) {
                JSONObject o = src.optJSONObject(i);
                if (o == null) continue;
                String empno = o.optString("empno", "");
                inListEmpnos.add(empno);

                boolean checked = (empno != null && !empno.isEmpty() && verifiedAccum.contains(empno));
                try { o.put("checked", checked); } catch (JSONException ignored) {}
                out.put(o);
            }
        } catch (Exception e) {
            Log.e(TAG_READY, "buildEmployeeJSONArrayWithChecked parse error: " + e.getMessage());
        }

        final Set<String> extrasAdded = new HashSet<>();
        for (Employee ex : extraPeople) {
            String empno = ex.empno != null ? ex.empno : "";
            if (empno.isEmpty()) continue;
            if (inListEmpnos.contains(empno)) {
                Log.d(TAG_READY, "Skip extra (already in original list) -> " + empno);
                continue;
            }
            if (!extrasAdded.add(empno)) {
                Log.d(TAG_READY, "Skip extra duplicate -> " + empno);
                continue;
            }

            JSONObject info = readHrmCache(empno);
            String dept = (info != null) ? info.optString("dept", "") : "";
            String sex  = (info != null) ? info.optString("sex",  "") : "";
            String name = (ex.name != null && !ex.name.isEmpty())
                    ? ex.name : (info != null ? info.optString("name","") : "");

            JSONObject add = new JSONObject();
            try {
                add.put("dept", dept);
                add.put("name", name);
                add.put("empno", empno);
                add.put("sex", sex);
                add.put("deputies", new JSONObject());
                add.put("searchArr", new JSONArray());
                add.put("checked", true); // đỏ => checked=true
                out.put(add);
                Log.d(TAG_READY, "Append extra -> empno=" + empno + ", dept=" + dept + ", sex=" + sex + ", name=" + name);
            } catch (JSONException ignored) {}
            if (info == null && (dept.isEmpty() || sex.isEmpty())) {
                Log.w(TAG_HRM, "Extra missing dept/sex & no cache -> late fetch HRM, empno=" + empno);
                fetchAndCacheHrm(empno);
            }
        }

        Log.d(TAG_READY, "Ready[] size=" + out.length());
        return out;
    }

    /** Lưu trip_id, qr_code, location + employee_list_raw + employee_list_ready + status_inout */
    private void cacheTripForPayload(Integer tripId, String qrRaw, String employeeListRaw) {
        try {
            SharedPreferences sp = requireContext()
                    .getSharedPreferences("trip_cache", android.content.Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();

            // raw
            String normalized = normalizeEmployeeList(employeeListRaw);
            ed.putString("employee_list_raw", normalized);

            // ready
            JSONArray ready = buildEmployeeJSONArrayWithChecked(normalized);
            ed.putString("employee_list_ready", ready.toString());

            // meta
            ed.putInt("trip_id", tripId != null ? tripId : 0);
            ed.putString("qr_code", (qrRaw != null) ? qrRaw : "");
            ed.putString("status_inout", statusInOut);

            String curLoc = sp.getString("location", null);
            if (curLoc == null || curLoc.trim().isEmpty()) {
                ed.putString("location", "1"); // default cổng trước
            }
            ed.apply();

            Log.d(TAG_CACHE,
                    "Cached -> trip_id=" + (tripId != null ? tripId : 0) +
                            ", qr_code=" + (qrRaw != null ? qrRaw : "") +
                            ", status_inout=" + statusInOut +
                            ", location=" + sp.getString("location","1") +
                            ", ready.size=" + ready.length());
        } catch (Exception e) {
            Log.e(TAG_CACHE, "cacheTripForPayload error: " + e.getMessage());
        }
    }

    /** User bấm xoá người nền đỏ */
    private void onRemoveExtraRequested(String empno, int adapterPosition) {
        if (empno == null || empno.trim().isEmpty()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa khỏi danh sách?")
                .setMessage("Bạn có chắc muốn xóa nhân sự này khỏi danh sách tạm thời?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> removeExtra(empno, adapterPosition))
                .show();
    }

    private void removeExtra(String empno, int pos) {
        // 1) Xoá khỏi danh sách “đỏ” ở Fragment
        for (int i = extraPeople.size() - 1; i >= 0; i--) {
            if (empno.equals(extraPeople.get(i).empno)) {
                extraPeople.remove(i);
                break;
            }
        }
        extraEmpnos.remove(empno);
        verifiedAccum.remove(empno); // phòng trường hợp có trong set xanh

        // 2) Xoá khỏi UI
        adapter.removeAt(pos);

        // 3) Re-cache để payload #1 loại người này ra khỏi ready[]
        cacheTripForPayload(lastTripId, lastQrCode, lastRawEmpList);

        toast("Đã xoá khỏi danh sách tạm thời");
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }
}
