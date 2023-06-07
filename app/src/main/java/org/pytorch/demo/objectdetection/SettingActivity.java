package org.pytorch.demo.objectdetection;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

        private CheckBoxPreference checkBox1;
        private CheckBoxPreference checkBox2;
        private CheckBoxPreference checkBox3;
        private CheckBoxPreference checkBox7;
        private CheckBoxPreference checkBox8;
        private SwitchPreference switch1;



        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.activity_setting, rootKey);

            checkBox1 = findPreference("checkbox1");
            checkBox7 = findPreference("checkbox7");
            checkBox8 = findPreference("checkbox8");
            switch1 = findPreference("switch1");



            // Set the listener for the switch preference
            switch1.setOnPreferenceChangeListener(this);
            checkBox7.setOnPreferenceChangeListener(this);
            checkBox8.setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if(preference.getKey().equals("checkbox7")){
                boolean flag2 = (boolean) newValue;
                MainActivity.showdepth = flag2;
            }
            if(preference.getKey().equals("checkbox8")){
                boolean flag3 = (boolean)  newValue;
                MainActivity.tts2 = flag3;
            }

            if (preference.getKey().equals("switch1")) {
                boolean flag1 = (boolean) newValue;
                flag1 = switch1.isChecked();
                if (!flag1) {
                    // Set the checkbox preference to false when the switch preference is true
                    checkBox1.setEnabled(false);
                    checkBox1.setChecked(false);
                }
                else if(flag1){
                    checkBox1.setEnabled(true);
                }
            }
            return true;
        }
    }
}