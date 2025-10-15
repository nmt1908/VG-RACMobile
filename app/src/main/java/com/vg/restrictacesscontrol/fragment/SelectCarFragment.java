// SelectCarFragment.java
package com.vg.restrictacesscontrol.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.vg.restrictacesscontrol.R;
import com.vg.restrictacesscontrol.adapter.CarAdapter;
import com.vg.restrictacesscontrol.models.Car;
import com.vg.restrictacesscontrol.service.ApiClient;

import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SelectCarFragment extends Fragment {

    // ==== MODE constants ====
    private static final String MODE_PLATE = "plate";
    private static final String MODE_ODO   = "odo";

    // ==== ARG keys ====
    private static final String ARG_TRIP_ID  = "arg_trip_id";
    private static final String ARG_QR_CODE  = "arg_qr_code";

    private static final String PREF_FILE   = "trip_cache";
    private static final String KEY_LOCATION = "location";     // "1" | "2"
    private static final String KEY_STATUS   = "status_inout"; // "in" | "out"

    // ==== LOG TAGs ====
    private static final String TAG_STATUS   = "BTA_STATUS";
    private static final String TAG_PAYLOAD1 = "BTA_PAYLOAD_1_SAVE";
    private static final String TAG_PAYLOAD2 = "BTA_PAYLOAD_2_CONFIRM";

    // ==== Args real values ====
    private int argTripId = 0;
    private String argQrCode = "";
    private com.google.android.material.button.MaterialButtonToggleGroup tgStatus;
    private com.google.android.material.button.MaterialButton btnOut, btnIn;

    // ==== Views ====
    private TextInputEditText edtCarNumber, edtKm, edtSecurityEmpno;
    private MaterialButton btnConfirm, btnToggleCars;
    private RecyclerView rvCars;
//    private FloatingActionButton btnCapture;

    // ==== Others ====
    private CarAdapter adapter;
    private String selectedLicNo = null;
    private AlertDialog captureDialog;
    private FloatingActionButton fabPlate, fabOdo;

    private AlertDialog successDialog;

    // UI state
    private boolean listVisible = false;
    private static final String KEY_LIST_VISIBLE = "list_visible";
    private static final String KEY_STATE_PLATE = "state_plate";
    private static final String KEY_STATE_ODO = "state_odo";

    // Activity Result launcher
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> scanLauncher;
    private AlertDialog confirmDialog;
    public interface OnConfirmListener {
        void onConfirm(String carNumber, String km, String empnoSecurity);
    }

    public static SelectCarFragment newInstance() {
        return new SelectCarFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scanLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        String mode = result.getData().getStringExtra("mode");
                        if (MODE_PLATE.equals(mode)) {
                            String plate = result.getData().getStringExtra("plate_number");
                            boolean fromCapture = result.getData().getBooleanExtra("from_capture", false);
                            if (plate != null && !plate.isEmpty()) {
                                edtCarNumber.setText(plate);
                                if (fromCapture) saveValue("plate_number", plate);
                                Snackbar.make(requireView(), "Đã nhận biển số: " + plate, Snackbar.LENGTH_SHORT).show();
                            }
                        } else if (MODE_ODO.equals(mode)) {
                            String odo = result.getData().getStringExtra("odo_reading");
                            boolean fromCapture = result.getData().getBooleanExtra("from_capture", false);
                            if (odo != null && !odo.isEmpty()) {
                                edtKm.setText(odo);
                                if (fromCapture) saveValue("odo_reading", odo);
                                Snackbar.make(requireView(), "Đã nhận Odo: " + odo, Snackbar.LENGTH_SHORT).show();
                            }
                        }
                        validate();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_select_car, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        edtCarNumber     = v.findViewById(R.id.edtCarNumber);
        edtKm            = v.findViewById(R.id.edtKm);
        edtSecurityEmpno = v.findViewById(R.id.edtSecurityEmpno);
        btnConfirm       = v.findViewById(R.id.btnConfirm);
        btnToggleCars    = v.findViewById(R.id.btnToggleCars);
        rvCars           = v.findViewById(R.id.rvCars);
//        btnCapture       = v.findViewById(R.id.btnCapture);
        tgStatus = v.findViewById(R.id.tgStatus);
        btnOut   = v.findViewById(R.id.btnOut);
        btnIn    = v.findViewById(R.id.btnIn);
        fabPlate = v.findViewById(R.id.fabPlate);
        fabOdo   = v.findViewById(R.id.fabOdo);

        // Lấy args (nếu có)
        Bundle args = getArguments();
        if (args != null) {
            argTripId   = args.getInt(ARG_TRIP_ID, 0);
            argQrCode   = args.getString(ARG_QR_CODE, "");
        }

        // Fallback từ cache + log snapshot cache
        android.content.SharedPreferences tripSP =
                requireContext().getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        if (argTripId <= 0) argTripId = tripSP.getInt("trip_id", 0);
        if (argQrCode == null || argQrCode.trim().isEmpty())
            argQrCode = tripSP.getString("qr_code", "");

        String cacheStatus = tripSP.getString(KEY_STATUS, null);
        String cacheLoc    = tripSP.getString(KEY_LOCATION, null);
        Log.d(TAG_STATUS, "Cache snapshot -> trip_id=" + argTripId
                + ", qr_code=" + argQrCode
                + ", location(raw)=" + cacheLoc
                + ", status_inout(raw)=" + cacheStatus);

        // Khôi phục UI state
        if (savedInstanceState != null) {
            listVisible = savedInstanceState.getBoolean(KEY_LIST_VISIBLE, false);
            rvCars.setVisibility(listVisible ? View.VISIBLE : View.GONE);
            edtCarNumber.setText(savedInstanceState.getString(KEY_STATE_PLATE, loadValue("plate_number")));
            edtKm.setText(savedInstanceState.getString(KEY_STATE_ODO, loadValue("odo_reading")));
        } else {
            rvCars.setVisibility(View.GONE);
        }
        refreshToggleIcon();

        // Recycler
        rvCars.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rvCars.setHasFixedSize(true);

        adapter = new CarAdapter(requireContext(), new ArrayList<>(), (car, position) -> {
            selectedLicNo = car.lic_no;
            edtCarNumber.setText(selectedLicNo);
            saveValue("plate_number", selectedLicNo);
            validate();
        });
        rvCars.setAdapter(adapter);

        if (btnToggleCars != null) {
            btnToggleCars.setOnClickListener(b -> {
                listVisible = !listVisible;
                ViewGroup root = tryGetRootContainer();
                if (root != null) {
                    TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(180));
                }
                rvCars.setVisibility(listVisible ? View.VISIBLE : View.GONE);
                refreshToggleIcon();
                if (listVisible) rvCars.post(() -> rvCars.scrollToPosition(0));
            });
        }

        // Dialog chọn chụp
//        btnCapture.setOnClickListener(v1 -> showCaptureDialog());
        fabPlate.setOnClickListener(v1 -> onCapturePlate());
        fabOdo.setOnClickListener(v1 -> onCaptureOdo());
        // Validate watcher
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { validate(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        edtCarNumber.addTextChangedListener(watcher);
        edtKm.addTextChangedListener(watcher);
        edtSecurityEmpno.addTextChangedListener(watcher);
        String initialStatus = readStatusFromCache(); // "in" | "out"
        if ("in".equalsIgnoreCase(initialStatus)) {
            tgStatus.check(btnIn.getId());
        } else {
            // Mặc định "out" nếu chưa có lịch sử
            tgStatus.check(btnOut.getId());
        }
        tgStatus.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return; // bỏ sự kiện uncheck tự nhiên của toggle group
            if (checkedId == btnOut.getId()) {
                persistStatus("out");
            } else if (checkedId == btnIn.getId()) {
                persistStatus("in");
            }
        });
        // Xác nhận -> in payload ra Logcat (status lấy từ SharedPreferences)
//        btnConfirm.setOnClickListener(v1 -> {
//            String carNumber = str(edtCarNumber);
//            String km        = str(edtKm);
//            String empnoSec  = str(edtSecurityEmpno);
//
//            logSaveDataInOutPayload(carNumber, km, empnoSec);
//            logConfirmInfoMulTripsPayload(carNumber, km, empnoSec);
//
//            if (getActivity() instanceof OnConfirmListener) {
//                ((OnConfirmListener) getActivity()).onConfirm(carNumber, km, empnoSec);
//            }
//        });
        btnConfirm.setOnClickListener(v1 -> {
            if (!btnConfirm.isEnabled()) return;
            showConfirmDialog();
        });

        // Load list xe
        fetchCars();
        validate();
    }
    private String readLocationRaw() {
        android.content.SharedPreferences sp =
                requireContext().getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        return sp.getString(KEY_LOCATION, "1");
    }
    private void showSuccessDialog(String message) {
        if (!isAdded()) return;
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_success_bta, null, false);

        // set nội dung động nếu muốn
        TextView tvMsg = view.findViewById(R.id.tvSuccessMsg);
        if (tvMsg != null && message != null && !message.trim().isEmpty()) {
            tvMsg.setText(message);
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.btnSuccessClose).setOnClickListener(v -> {
            dlg.dismiss();
            resetUiAfterSuccess(); // reset form sau khi đóng
        });

        dlg.show();
        successDialog = dlg;
    }

    private void resetUiAfterSuccess() {
        // Giống hành vi Vue sau khi confirm thành công
        edtCarNumber.setText("");
        edtKm.setText("");
        edtSecurityEmpno.setText("");
        rvCars.setVisibility(View.GONE);
        listVisible = false;
        refreshToggleIcon();
    }

    // === giữ đúng kiểu cho payload #2: 1/2 -> Integer, còn lại giữ String (vd "tb") ===
    private Object coerceLocationForPayload2(String raw) {
        if ("1".equals(raw)) return 1;
        if ("2".equals(raw)) return 2;
        return raw; // "tb","tc",...
    }
    private void persistStatus(String status) {
        android.content.SharedPreferences sp =
                requireContext().getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        sp.edit().putString(KEY_STATUS, status).apply();
        Log.d(TAG_STATUS, "User switched status -> " + status);
    }
    private void showConfirmDialog() {
        if (!isAdded()) return;
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_bta, null, false);

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dlg.dismiss());
        view.findViewById(R.id.btnOk).setOnClickListener(v -> {
            dlg.dismiss();
            submitToApis(); // gọi 2 API như Vue
        });

        dlg.show();
        confirmDialog = dlg;
    }

    // === Gửi 2 API: saveDataInAndOutOfGateBTANew -> confirmInfoMulTrips ===
    private void submitToApis() {
        // chặn spam
        btnConfirm.setEnabled(false);

        try {
            // --- Payload #1 (giống Vue) ---
            JSONArray dataArr = buildEmployeeDataArray();

            JSONObject p1 = new JSONObject();
            p1.put("data", dataArr);
            p1.put("appInProgress", "vg-bta");
            if (argTripId > 0) p1.put("idAppInProgress", argTripId);

            int locCode = readLocationCode();               // 1/2
            String status = readStatusFromCache();          // "in" | "out"
            p1.put("status", status);
            p1.put("location", codeToText(locCode));        // "front gate" | "back gate"
            p1.put("employee_list", dataArr.length());

            RequestBody body1 = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    p1.toString()
            );

            ApiClient.get().saveDataInOutOfGateBTANew(body1).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> resp) {
                    if (!isAdded()) return;

                    boolean updated = false;
                    try {
                        if (resp.isSuccessful() && resp.body() != null) {
                            String s = resp.body().string();
                            // server có thể trả {"status":"updated"} hoặc "updated"
                            JsonElement je = JsonParser.parseString(s);
                            if (je.isJsonObject() && je.getAsJsonObject().has("status")) {
                                updated = "updated".equalsIgnoreCase(
                                        je.getAsJsonObject().get("status").getAsString());
                            } else {
                                updated = "updated".equalsIgnoreCase(s.replace("\"", "").trim());
                            }
                        }
                    } catch (Exception ignore) {}

                    if (updated) {
                        postConfirmInfo(); // gọi API #2
                    } else {
                        btnConfirm.setEnabled(true);
                        Snackbar.make(requireView(), "Lưu trạng thái (B1) thất bại", Snackbar.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!isAdded()) return;
                    btnConfirm.setEnabled(true);
                    Snackbar.make(requireView(), "Lỗi mạng (B1): " + t.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            btnConfirm.setEnabled(true);
            Snackbar.make(requireView(), "Lỗi chuẩn bị dữ liệu: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    // === API #2: confirmInfoMulTrips ===
    private void postConfirmInfo() {
        try {
            JSONObject p2 = new JSONObject();
            if (argTripId > 0) p2.put("id", argTripId);
            if (argQrCode != null && !argQrCode.trim().isEmpty()) p2.put("qr_code", argQrCode);

            String carNumber = str(edtCarNumber);
            String km        = str(edtKm);
            String empnoSec  = str(edtSecurityEmpno);

            p2.put("car_number", carNumber == null ? "" : carNumber.toUpperCase());
            p2.put("kmCar", km == null ? "" : km.trim());

            String status = readStatusFromCache(); // "in" | "out"
            p2.put("status", status);

            // empno_security
            JSONObject empSec = new JSONObject();
            empSec.put("empno", empnoSec == null ? "" : empnoSec.toUpperCase());

            String rawLoc = readLocationRaw(); // "1","2","tb","tc"
            Object locVal = coerceLocationForPayload2(rawLoc);
            if (locVal instanceof Integer) empSec.put("location", (Integer) locVal);
            else                           empSec.put("location", String.valueOf(locVal));

            p2.put("empno_security", empSec);

            RequestBody body2 = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    p2.toString()
            );

            ApiClient.get().confirmInfoMulTrips(body2).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> resp) {
                    if (!isAdded()) return;

                    boolean updated = false;
                    try {
                        if (resp.isSuccessful() && resp.body() != null) {
                            String s = resp.body().string();
                            JsonElement je = JsonParser.parseString(s);
                            if (je.isJsonObject() && je.getAsJsonObject().has("status")) {
                                updated = "updated".equalsIgnoreCase(
                                        je.getAsJsonObject().get("status").getAsString());
                            } else {
                                updated = "updated".equalsIgnoreCase(s.replace("\"", "").trim());
                            }
                        }
                    } catch (Exception ignore) {}

                    if (updated) {
                        // reset nhẹ UI giống Vue
                        edtCarNumber.setText("");
                        edtKm.setText("");
                        edtSecurityEmpno.setText("");
                        rvCars.setVisibility(View.GONE);
                        listVisible = false;
                        showSuccessDialog(getString(R.string.infomation_save_sucessfully));

                        refreshToggleIcon();
                    } else {
                        Snackbar.make(requireView(), "Xác nhận (B2) thất bại", Snackbar.LENGTH_LONG).show();
                    }
                    btnConfirm.setEnabled(true);
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!isAdded()) return;
                    btnConfirm.setEnabled(true);
                    Snackbar.make(requireView(), "Lỗi mạng (B2): " + t.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            btnConfirm.setEnabled(true);
            Snackbar.make(requireView(), "Lỗi chuẩn bị dữ liệu B2: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }
    // ====== LOCATION helpers ======
    private int readLocationCode() {
        android.content.SharedPreferences sp =
                requireContext().getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        String v = sp.getString(KEY_LOCATION, "1");
        if ("2".equals(v)) return 2;
        if ("1".equals(v)) return 1;
        if ("back gate".equalsIgnoreCase(v)) return 2; // fallback kiểu cũ
        return 1; // default front
    }
    private String codeToText(int code) {
        return (code == 2) ? "back gate" : "front gate";
    }

    // ====== STATUS helper: đọc từ SharedPreferences (giống Vue) ======
    private String readStatusFromCache() {
        android.content.SharedPreferences sp =
                requireContext().getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_STATUS, "out");
        String finalVal;
        if ("in".equalsIgnoreCase(raw)) {
            finalVal = "in";
        } else if ("out".equalsIgnoreCase(raw)) {
            finalVal = "out";
        } else {
            finalVal = "out";
        }
        Log.d(TAG_STATUS, "readStatusFromCache -> raw=\"" + raw + "\" => final=\"" + finalVal + "\"");
        return finalVal;
    }

    // ===== Build data[] (giữ nguyên) =====
    private JSONArray buildEmployeeDataArray() {
        JSONArray dataArr = new JSONArray();
        try {
            android.content.SharedPreferences sp =
                    requireContext().getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);

            String ready = sp.getString("employee_list_ready", null);
            String raw   = sp.getString("employee_list_raw", null);
            String sourceJson = (ready != null && !ready.trim().isEmpty()) ? ready : raw;

            Log.d(TAG_PAYLOAD1, "Build data[] -> use=" + ((ready != null && !ready.trim().isEmpty()) ? "ready" : "raw"));
            if (sourceJson == null || sourceJson.trim().isEmpty()) {
                Log.w(TAG_PAYLOAD1, "employee_list_ready/raw rỗng -> data[] sẽ rỗng");
                return dataArr;
            }

            JSONArray src = new JSONArray(sourceJson);
            Log.d(TAG_PAYLOAD1, "Source size=" + src.length());

            for (int i = 0; i < src.length(); i++) {
                JSONObject empIn = src.optJSONObject(i);
                if (empIn == null) continue;

                JSONObject empOut = new JSONObject();
                empOut.put("dept",  empIn.optString("dept", ""));
                empOut.put("name",  empIn.optString("name", ""));
                empOut.put("empno", empIn.optString("empno", ""));
                empOut.put("sex",   empIn.optString("sex", ""));

                JSONObject deputies = empIn.optJSONObject("deputies");
                if (deputies == null) deputies = new JSONObject();
                if (!deputies.has("name"))       deputies.put("name", "");
                if (!deputies.has("empno"))      deputies.put("empno", "");
                if (!deputies.has("dept"))       deputies.put("dept", "");
                if (!deputies.has("sex"))        deputies.put("sex", "");
                if (!deputies.has("empnoName"))  deputies.put("empnoName", "");
                empOut.put("deputies", deputies);

                JSONArray searchArr = empIn.optJSONArray("searchArr");
                if (searchArr == null) searchArr = new JSONArray();
                empOut.put("searchArr", searchArr);

                boolean checked = empIn.optBoolean("checked", false);
                empOut.put("checked", checked);

                // Điền thiếu từ HRM cache nếu có
                fillFromHrmCacheIfMissing(empOut);

                dataArr.put(empOut);
            }

            Log.d(TAG_PAYLOAD1, "Build data[] done -> size=" + dataArr.length());
        } catch (Exception e) {
            Log.e(TAG_PAYLOAD1, "buildEmployeeDataArray error: " + e.getMessage());
        }
        return dataArr;
    }

    private void fillFromHrmCacheIfMissing(JSONObject empOut) {
        try {
            String empno = empOut.optString("empno", "");
            if (empno.isEmpty()) return;
            android.content.SharedPreferences sp =
                    requireContext().getSharedPreferences("hrm_cache", android.content.Context.MODE_PRIVATE);
            String s = sp.getString(empno, null);
            if (s == null) {
                Log.d(TAG_PAYLOAD1, "HRM cache MISS at build payload -> empno=" + empno);
                return;
            }
            JSONObject hrm = new JSONObject(s);

            if (empOut.optString("dept","").isEmpty()) empOut.put("dept", hrm.optString("dept",""));
            if (empOut.optString("sex","").isEmpty())  empOut.put("sex",  hrm.optString("sex",""));
            if (empOut.optString("name","").isEmpty()) empOut.put("name", hrm.optString("name",""));
        } catch (Exception ignored) {}
    }

    // ===== In payload #1 =====
    private void logSaveDataInOutPayload(String carNumber, String km, String empnoSecurity) {
        try {
            JSONObject root = new JSONObject();

            JSONArray dataArr = buildEmployeeDataArray();
            root.put("data", dataArr);

            root.put("appInProgress", "vg-bta");
            if (argTripId > 0) root.put("idAppInProgress", argTripId);

            int locCode = readLocationCode();
            String status = readStatusFromCache();
            Log.d(TAG_STATUS, "Payload#1 will use status: " + status + " | location(code)=" + locCode + ", text=" + codeToText(locCode));

            root.put("status", status);                 // "in" | "out"
            root.put("location", codeToText(locCode));  // "front gate" | "back gate"
            root.put("employee_list", dataArr.length());

            Log.d(TAG_PAYLOAD1, root.toString(2));
            Log.d(TAG_PAYLOAD1, "(mock) response: {\"status\":\"updated\"}");

            if (argTripId <= 0) {
                Log.w(TAG_PAYLOAD1, "⚠️ Thiếu idAppInProgress (arg_trip_id) — đang để trống key.");
            }
        } catch (Exception e) {
            Log.e(TAG_PAYLOAD1, "JSON build error: " + e.getMessage());
        }
    }

    // ===== In payload #2 =====
    private void logConfirmInfoMulTripsPayload(String carNumber, String km, String empnoSecurity) {
        try {
            JSONObject root = new JSONObject();

            if (argTripId > 0) root.put("id", argTripId);
            if (argQrCode != null && !argQrCode.trim().isEmpty()) root.put("qr_code", argQrCode);

            root.put("car_number", (carNumber == null) ? "" : carNumber.trim());
            root.put("kmCar", (km == null) ? "" : km.trim());

            int locCode = readLocationCode();
            String status = readStatusFromCache();
            Log.d(TAG_STATUS, "Payload#2 will use status: " + status + " | location(code)=" + locCode + ", text=" + codeToText(locCode));

            JSONObject empSec = new JSONObject();
            empSec.put("empno", (empnoSecurity == null) ? "" : empnoSecurity.trim());
            empSec.put("location", locCode); // GIỮ số theo yêu cầu
            root.put("empno_security", empSec);

            root.put("status", status); // "in" | "out"

            Log.d(TAG_PAYLOAD2, root.toString(2));
            Log.d(TAG_PAYLOAD2, "(mock) response: updated");

            if (argTripId <= 0)
                Log.w(TAG_PAYLOAD2, "⚠️ Thiếu id (arg_trip_id) — đang bỏ trống key.");
            if (argQrCode == null || argQrCode.trim().isEmpty())
                Log.w(TAG_PAYLOAD2, "⚠️ Thiếu qr_code — đang bỏ trống key.");
            if (carNumber == null || carNumber.trim().isEmpty())
                Log.w(TAG_PAYLOAD2, "⚠️ Thiếu car_number — đã set \"\".");
            if (km == null || km.trim().isEmpty())
                Log.w(TAG_PAYLOAD2, "⚠️ Thiếu kmCar — đã set \"\".");
            if (empnoSecurity == null || empnoSecurity.trim().isEmpty())
                Log.w(TAG_PAYLOAD2, "⚠️ Thiếu empno_security.empno — đã set \"\".");
        } catch (Exception e) {
            Log.e(TAG_PAYLOAD2, "JSON build error: " + e.getMessage());
        }
    }

    private void showCaptureDialog() {
        if (!isAdded()) return;
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_capture_choice, null, false);

        captureDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        if (captureDialog.getWindow() != null) {
            captureDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View btnPlate = dialogView.findViewById(R.id.btnPlate);
        View btnOdo   = dialogView.findViewById(R.id.btnOdo);
        View btnCancel= dialogView.findViewById(R.id.btnCancel);

        btnPlate.setOnClickListener(v -> {
            if (captureDialog != null) captureDialog.dismiss();
            onCapturePlate();
        });
        btnOdo.setOnClickListener(v -> {
            if (captureDialog != null) captureDialog.dismiss();
            onCaptureOdo();
        });
        btnCancel.setOnClickListener(v -> {
            if (captureDialog != null) captureDialog.dismiss();
        });

        captureDialog.show();
    }

    private void onCapturePlate() {
        Intent it = new Intent(requireContext(), com.vg.restrictacesscontrol.activitys.CapturePlateActivity.class);
        it.putExtra("mode", MODE_PLATE);
        scanLauncher.launch(it);
    }

    private void onCaptureOdo() {
        Intent it = new Intent(requireContext(), com.vg.restrictacesscontrol.activitys.CapturePlateActivity.class);
        it.putExtra("mode", MODE_ODO);
        scanLauncher.launch(it);
    }

    private void refreshToggleIcon() {
        if (btnToggleCars == null) return;
        if (listVisible) {
            btnToggleCars.setText(R.string.hide_car_list);
            btnToggleCars.setIconResource(R.drawable.baseline_keyboard_arrow_up_24);
        } else {
            btnToggleCars.setText(R.string.car_list);
            btnToggleCars.setIconResource(R.drawable.baseline_keyboard_arrow_down_24);
        }
    }

    private ViewGroup tryGetRootContainer() {
        View root = getView();
        if (root == null) return null;
        View rc = root.findViewById(R.id.rootContainer);
        if (rc instanceof ViewGroup) return (ViewGroup) rc;
        if (root instanceof ViewGroup) return (ViewGroup) root;
        return null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_LIST_VISIBLE, listVisible);
        outState.putString(KEY_STATE_PLATE, str(edtCarNumber));
        outState.putString(KEY_STATE_ODO, str(edtKm));
    }

    private void validate() {
        boolean ok = !str(edtCarNumber).isEmpty()
                && !str(edtKm).isEmpty()
                && !str(edtSecurityEmpno).isEmpty();
        btnConfirm.setEnabled(ok);
    }

    private String str(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void saveValue(String key, String value) {
        requireContext().getSharedPreferences("scan_cache", android.content.Context.MODE_PRIVATE)
                .edit().putString(key, value).apply();
    }

    private String loadValue(String key) {
        return requireContext().getSharedPreferences("scan_cache", android.content.Context.MODE_PRIVATE)
                .getString(key, "");
    }

    private void fetchCars() {
        btnConfirm.setEnabled(false);

        ApiClient.get().getCarList().enqueue(new Callback<List<Car>>() {
            @Override
            public void onResponse(@NonNull Call<List<Car>> call, @NonNull Response<List<Car>> resp) {
                if (!isAdded()) return;
                if (resp.isSuccessful() && resp.body() != null) {
                    List<Car> cars = resp.body();
                    adapter = new CarAdapter(requireContext(), cars, (car, pos) -> {
                        selectedLicNo = car.lic_no;
                        edtCarNumber.setText(selectedLicNo);
                        saveValue("plate_number", selectedLicNo);
                        validate();
                    });
                    rvCars.setAdapter(adapter);
                } else {
                    Snackbar.make(requireView(), "Không tải được danh sách xe", Snackbar.LENGTH_LONG).show();
                }
                validate();
            }

            @Override
            public void onFailure(@NonNull Call<List<Car>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Snackbar.make(requireView(), "Lỗi mạng: " + t.getMessage(), Snackbar.LENGTH_LONG).show();
                validate();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (captureDialog != null && captureDialog.isShowing()) {
            captureDialog.dismiss();
        }
        if (successDialog != null && successDialog.isShowing()) {
            successDialog.dismiss();
        }
        captureDialog = null;
        successDialog = null;
        super.onDestroyView();
    }

}
