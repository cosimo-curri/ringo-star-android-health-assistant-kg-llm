package com.example.ringo_star;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import com.example.ringo_star.security.SecureActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.ringo_star.data.DatabaseClient;
import com.example.ringo_star.data.entity.User;
import com.example.ringo_star.enumeration.BloodGroup;
import com.example.ringo_star.enumeration.Gender;
import com.example.ringo_star.utils.PinUtils;
import com.example.ringo_star.utils.ProfileGraphUpdater;
import com.example.ringo_star.utils.RingoStarRDF4J;
import com.kevalpatel2106.rulerpicker.RulerValuePicker;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.rdf4j.model.Model;

public class UserInfoActivity extends SecureActivity {
    @Override protected boolean requiresUnlock() { return getIntent() == null || !getIntent().getBooleanExtra("onboarding", false); }

    private final ExecutorService profileExecutor = Executors.newSingleThreadExecutor();
    Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_user_info);

        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.black));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        NumberPicker dayPicker = findViewById(R.id.dayPicker);
        NumberPicker monthPicker = findViewById(R.id.monthPicker);
        NumberPicker yearPicker = findViewById(R.id.yearPicker);

        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
        int currentMonth = calendar.get(Calendar.MONTH) + 1;
        int currentYear = calendar.get(Calendar.YEAR);

        yearPicker.setMinValue(1900);
        yearPicker.setMaxValue(2100);
        yearPicker.setValue(currentYear);

        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(currentMonth);

        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(getDaysInMonth(currentYear, currentMonth));
        dayPicker.setValue(currentDay);

        dayPicker.setWrapSelectorWheel(true);
        monthPicker.setWrapSelectorWheel(true);
        yearPicker.setWrapSelectorWheel(true);

        NumberPicker.OnValueChangeListener listener = (picker, oldVal, newVal) -> {
            int selectedYear = yearPicker.getValue();
            int selectedMonth = monthPicker.getValue();
            int selectedDay = dayPicker.getValue();

            int maxDays = getDaysInMonth(selectedYear, selectedMonth);
            dayPicker.setMaxValue(maxDays);

            if(selectedDay > maxDays) {
                dayPicker.setValue(maxDays);
            }
        };

        yearPicker.setOnValueChangedListener(listener);
        monthPicker.setOnValueChangedListener(listener);
        dayPicker.setOnValueChangedListener(listener);

        Spinner bloodGroupSpinner = findViewById(R.id.bloodGroupSpinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.bloodGroupSpinner,
                android.R.layout.simple_spinner_item
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        bloodGroupSpinner.setAdapter(adapter);

        RulerValuePicker rulerPickerHeight = findViewById(R.id.rulerPickerHeight);
        RulerValuePicker rulerPickerWeight = findViewById(R.id.rulerPickerWeight);

        rulerPickerHeight.selectValue(180);
        rulerPickerWeight.selectValue(80);

        EditText txtFirstname = findViewById(R.id.txtFirstname);
        EditText txtLastname = findViewById(R.id.txtLastname);
        EditText txtPIN = findViewById(R.id.txtPIN);

        RadioGroup radioGroupGender = findViewById(R.id.radioGroupGender);

        CheckBox smoke = findViewById(R.id.smoke);

        Button btnOk = findViewById(R.id.btnOk);

        btnOk.setEnabled(false);
        profileExecutor.execute(() -> {
            try {
            DatabaseClient databaseClient = DatabaseClient.getInstance(getApplicationContext());
            User loaded = databaseClient.getDatabase().userDAO().getUser();

            if (loaded != null) {
                runOnUiThread(() -> {
                Gender gender = loaded.getGender();
                LocalDate birthday = loaded.getBirthday();
                BloodGroup bloodGroup = loaded.getBloodGroup();
                boolean isSmoke = loaded.isSmoke();

                txtFirstname.setText(loaded.getFirstname());
                txtLastname.setText(loaded.getLastname());

                if(gender == Gender.MALE)
                    radioGroupGender.check(R.id.radioButtonMale);
                else
                    radioGroupGender.check(R.id.radioButtonFemale);

                int dayBirthday = birthday.getDayOfMonth();
                int monthBirthday = birthday.getMonthValue();
                int yearBirthday = birthday.getYear();

                dayPicker.setValue(dayBirthday);
                monthPicker.setValue(monthBirthday);
                yearPicker.setValue(yearBirthday);

                rulerPickerHeight.selectValue(loaded.getHeight());
                rulerPickerWeight.selectValue(loaded.getWeight());

                if(bloodGroup == BloodGroup.A_positive)
                    bloodGroupSpinner.setSelection(0);
                else if(bloodGroup == BloodGroup.A_negative)
                    bloodGroupSpinner.setSelection(1);
                else if(bloodGroup == BloodGroup.B_positive)
                    bloodGroupSpinner.setSelection(2);
                else if(bloodGroup == BloodGroup.B_negative)
                    bloodGroupSpinner.setSelection(3);
                else if(bloodGroup == BloodGroup.AB_positive)
                    bloodGroupSpinner.setSelection(4);
                else if(bloodGroup == BloodGroup.AB_negative)
                    bloodGroupSpinner.setSelection(5);
                else if(bloodGroup == BloodGroup.zero_positive)
                    bloodGroupSpinner.setSelection(6);
                else if(bloodGroup == BloodGroup.zero_negative)
                    bloodGroupSpinner.setSelection(7);

                smoke.setChecked(isSmoke);
                });
            }
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) btnOk.setEnabled(true); });
            } catch (RuntimeException failure) {
                Log.e("RingoStar", "Unable to load profile", failure);
                runOnUiThread(() -> Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_LONG).show());
            }
        });

        btnOk.setOnClickListener(v -> {
            String firstname = txtFirstname.getText().toString().trim();
            String lastname = txtLastname.getText().toString().trim();
            String pin = txtPIN.getText().toString();
            int selectedId = radioGroupGender.getCheckedRadioButtonId();
            if (firstname.isEmpty() || lastname.isEmpty() || !pin.matches("[0-9]{4}")
                    || selectedId == -1) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
                ConstraintLayout main = findViewById(R.id.main);
                Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
                main.startAnimation(shake);
                Toast.makeText(this, R.string.toast_all_fields_requires, Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selected = findViewById(selectedId);
            final User requested = new User();
            try {
                requested.setFirstname(firstname);
                requested.setLastname(lastname);
                requested.setGender(Gender.valueOf(selected.getText().toString().toUpperCase(java.util.Locale.ROOT)));
                requested.setBirthday(LocalDate.of(yearPicker.getValue(), monthPicker.getValue(), dayPicker.getValue()));
                requested.setHeight(rulerPickerHeight.getCurrentValue());
                requested.setWeight(rulerPickerWeight.getCurrentValue());
                requested.setBloodGroup(BloodGroup.values()[bloodGroupSpinner.getSelectedItemPosition()]);
                requested.setSmoke(smoke.isChecked());
            } catch (RuntimeException invalid) {
                Toast.makeText(this, R.string.toast_all_fields_requires, Toast.LENGTH_SHORT).show();
                return;
            }

            btnOk.setEnabled(false);
            profileExecutor.execute(() -> {
                User previous = null;
                boolean databaseUpdated = false;
                try {
                    byte[] salt = PinUtils.generateSalt();
                    requested.setSalt(salt);
                    requested.setHashedPin(PinUtils.hashPin(pin, salt));

                    Context app = getApplicationContext();
                    DatabaseClient db = DatabaseClient.getInstance(app);
                    previous = db.getDatabase().userDAO().getUser();
                    // Prepare the graph before changing Room, so bad RDF fails early.
                    Model oldGraph = RingoStarRDF4J.loadModelFromFile(app, "kg.ttl");
                    Model newGraph = ProfileGraphUpdater.apply(oldGraph, requested, LocalDate.now());
                    if (previous == null) {
                        db.getDatabase().userDAO().insert(requested);
                    } else {
                        requested.setId(previous.getId());
                        db.getDatabase().userDAO().update(requested);
                    }
                    databaseUpdated = true;
                    // The encrypted graph write is replace-atomic. A process interruption
                    // between the two commits is repaired from Room before inference.
                    RingoStarRDF4J.saveModelToFile(newGraph, app, "kg.ttl");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        }
                    });
                } catch (Exception failure) {
                    if (databaseUpdated) {
                        try {
                            if (previous == null) {
                                DatabaseClient.getInstance(getApplicationContext()).getDatabase().userDAO().delete();
                            } else {
                                DatabaseClient.getInstance(getApplicationContext()).getDatabase().userDAO().update(previous);
                            }
                        } catch (RuntimeException rollbackFailure) {
                            failure.addSuppressed(rollbackFailure);
                        }
                    }
                    Log.e("RingoStar", "Unable to commit profile", failure);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            btnOk.setEnabled(true);
                            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
    }

    @Override protected void onDestroy() {
        profileExecutor.shutdown();
        super.onDestroy();
    }

    private int getDaysInMonth(int year, int month) {
        switch(month) {
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;

            case 2:
                return (isLeapYear(year)) ? 29 : 28;

            default:
                return 31;
        }
    }

    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}