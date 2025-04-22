package com.example.groupprojectapplication.recordings;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.fragment.app.DialogFragment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {
    private TextView tvTimeDisplay;
    private int hour, minute;
    public TimePickerFragment(TextView tv) {
        tvTimeDisplay = tv;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        System.out.println("Displaying Date Picker");
        // Use the current time as the default values for the picker.
        final Calendar c = Calendar.getInstance();
        hour = c.get(Calendar.HOUR_OF_DAY);
        minute = c.get(Calendar.MINUTE);

        // Create a new instance of TimePickerDialog and return it.
        return new TimePickerDialog(getActivity(), this, hour, minute,
                DateFormat.is24HourFormat(getActivity()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        if (minute < 10) {
            tvTimeDisplay.setText(hourOfDay + ":0" + minute);
        } else {
            tvTimeDisplay.setText(hourOfDay + ":" + minute);
        }
        this.hour = hourOfDay;
        this.minute = minute;
    }

    public LocalTime getTime() {
        return LocalTime.of(hour, minute);
    }
}
