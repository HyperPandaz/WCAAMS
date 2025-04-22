package com.example.groupprojectapplication.recordings;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.DatePicker;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import java.time.LocalDate;

public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {
    private TextView tvDateDisplay;
    private int day, month, year;
    public DatePickerFragment(TextView tv) {
        tvDateDisplay = tv;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        System.out.println("Displaying Date Picker");
        // Use the current date as the default date in the picker.
        final Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
        day = c.get(Calendar.DAY_OF_MONTH);

        // Create a new instance of DatePickerDialog and return it.
        return new DatePickerDialog(requireContext(), this, year, month, day);
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        month += 1; //month numbering starts at 0 so offset
        tvDateDisplay.setText(dayOfMonth + "/" + month + "/" + year);
        day = dayOfMonth;
        this.month = month;
        this.year = year;
    }

    public LocalDate getDate() {
        return LocalDate.of(year, month, day);
    }
}
