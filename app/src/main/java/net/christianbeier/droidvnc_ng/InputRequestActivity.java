/*
 * DroidVNC-NG activity for requesting input/a11y permissions.
 *
 * Author: Christian Beier <info@christianbeier.net>
 *
 * Copyright (C) 2020 Kitchen Armor.
 *
 * You can redistribute and/or modify this program under the terms of the
 * GNU General Public License version 2 as published by the Free Software
 * Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place Suite 330, Boston, MA 02111-1307, USA.
 */

package net.christianbeier.droidvnc_ng;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class InputRequestActivity extends AppCompatActivity {

    private static final String TAG = "InputRequestActivity";
    private static final int REQUEST_INPUT = 43;
    static final String EXTRA_DO_NOT_START_MAIN_SERVICE_ON_FINISH = "do_not_start_main_service_on_finish";
    private boolean mDoNotStartMainServiceOnFinish;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getIntent().getBooleanExtra(EXTRA_DO_NOT_START_MAIN_SERVICE_ON_FINISH, false)) {
            mDoNotStartMainServiceOnFinish = true;
        }

        /*
            Get whether input and/or start-on-boot are requested; directly or from prefs as fallback.
         */
        boolean startOnBootRequested = Build.VERSION.SDK_INT >= 30 && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREFS_KEY_SETTINGS_START_ON_BOOT,  new Defaults(this).getStartOnBoot());
        boolean inputRequested = getIntent().hasExtra(MainService.EXTRA_VIEW_ONLY)
                ? !getIntent().getBooleanExtra(MainService.EXTRA_VIEW_ONLY, new Defaults(this).getViewOnly())
                : !PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREFS_KEY_SETTINGS_VIEW_ONLY,  new Defaults(this).getViewOnly());

        Log.d(TAG, "onCreate: input requested: " + inputRequested + " start on boot requested: " + startOnBootRequested);


        /*
            If neither input nor start-on-boot are requested, bail out early without bothering the user.
         */
        if(!inputRequested && !startOnBootRequested) {
            postResultAndFinish(false);
            return;
        }

        /*
            Then, decide on message to be shown.
         */
        int msg;
        if (inputRequested && startOnBootRequested) {
            // input and boot requested
            msg = R.string.input_a11y_msg_input_and_boot;
        } else if (inputRequested) {
            // input requested
            msg = R.string.input_a11y_msg_input;
        } else {
            // boot requested
            msg = R.string.input_a11y_msg_boot;
        }

        if(!InputService.isConnected()) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle(R.string.input_a11y_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);

                        // highlight entry on some devices, see https://stackoverflow.com/a/63214655/361413
                        final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
                        final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args";
                        Bundle bundle = new Bundle();
                        String showArgs = getPackageName() + "/" + InputService.class.getName();
                        bundle.putString(EXTRA_FRAGMENT_ARG_KEY, showArgs);
                        intent.putExtra(EXTRA_FRAGMENT_ARG_KEY, showArgs);
                        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle);

                        if (intent.resolveActivity(getPackageManager()) != null && !intent.resolveActivity(getPackageManager()).toString().contains("Stub"))
                            startActivityForResult(intent, REQUEST_INPUT);
                        else
                            new AlertDialog.Builder(InputRequestActivity.this)
                                    .setMessage(R.string.input_a11y_act_not_found_msg)
                                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                        Intent generalSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
                                        try {
                                            startActivityForResult(generalSettingsIntent, REQUEST_INPUT);
                                        } catch(ActivityNotFoundException ignored) {
                                            // This should not happen, but there were crashes reported from flaky devices
                                            // so in this case do nothing instead of crashing.
                                        }
                                    })
                                    .show();
                    })
                    .setNegativeButton(getString(R.string.no), (dialog, which) -> postResultAndFinish(false))
                    .show();
        } else {
            postResultAndFinish(true);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INPUT) {
            Log.d(TAG, "onActivityResult");
            postResultAndFinish(InputService.isConnected());
        }
    }

    private void postResultAndFinish(boolean isA11yEnabled) {

        if (isA11yEnabled)
            Log.i(TAG, "a11y enabled");
        else
            Log.i(TAG, "a11y disabled");

        if(!mDoNotStartMainServiceOnFinish) {
            Intent intent = new Intent(this, MainService.class);
            intent.setAction(MainService.ACTION_HANDLE_INPUT_RESULT);
            intent.putExtra(MainService.EXTRA_INPUT_RESULT, isA11yEnabled);
            intent.putExtra(MainService.EXTRA_ACCESS_KEY, PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREFS_KEY_SETTINGS_ACCESS_KEY, new Defaults(this).getAccessKey()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        finish();
    }

}