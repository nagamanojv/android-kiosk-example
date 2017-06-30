package com.sureshjoshi.android.kioskexample;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import com.sureshjoshi.android.kioskexample.utils.AppVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class MainActivity extends Activity {

    private static final File DOWNLOAD_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    public static final String ACTION_INSTALL_COMPLETE = "com.sureshjoshi.android.kioskexample.INSTALL_COMPLETE";
    public static final String ACTION_EXIT_KIOSK_MODE = "com.sureshjoshi.android.kioskexample.EXIT_KIOSK_MODE";

    @Bind(R.id.button_toggle_kiosk)
    Button mButton;

    @OnClick(R.id.button_toggle_kiosk)
    void toggleKioskMode() {
        enableKioskMode(!mIsKioskEnabled);
    }

    @OnClick(R.id.button_check_update)
    public void checkForUpdate() {
        // Look in downloaded directory, assuming it's called to "kiosk-x.y.z.apk"
        File files[] = DOWNLOAD_DIRECTORY.listFiles((dir, filename) -> {
            String lowerFilename = filename.toLowerCase();
            return lowerFilename.endsWith(".apk") && lowerFilename.contains("kiosk-");
        });

        if (files == null) {
            Timber.d("No files in downloads directory");
            return;
        }

        String applicationVersion = AppVersion.getApplicationVersion(this);

        // Figure out if the APK is newer than the current one
        // Base this off of filename convention
        for (File file : files) {
            String fileVersion = file.getName().substring(6, 11);
            Timber.d("Current filename is: %s, with version: %s", file.getName(), fileVersion);

            AppVersion appVersion = new AppVersion(fileVersion);
            int result = appVersion.compareTo(new AppVersion(AppVersion.getApplicationVersion(this)));
            if (result >= 1) {
                Timber.d("Application %s is older than %s", applicationVersion, fileVersion);
                try {
                    FileInputStream fis = new FileInputStream(file);
                    installPackage(getApplicationContext(), fis, "pathik.stw.installer");
                } catch (IOException e) {
                    Timber.d("Application installation failed. File problem : %s", e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    Timber.d("Application installation failed. Other problem : %s", e.getMessage());
                    e.printStackTrace();
                }
//                final Intent installIntent = new Intent(Intent.ACTION_VIEW);
//                installIntent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
//                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                startActivity(installIntent);
                break;
            } else if (result == 0) {
                Timber.d("Application %s is same as %s", applicationVersion, fileVersion);
            } else {
                Timber.d("Application %s is newer than %s", applicationVersion, fileVersion);
            }
        }
    }

    public static boolean installPackage(Context context, InputStream in, String packageName)
            throws IOException {
        Timber.d("Application %s installation started", packageName);
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        // set params
        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        OutputStream out = session.openWrite("COSU", 0, -1);
        byte[] buffer = new byte[65536];
        int c;
        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
        session.fsync(out);
        in.close();
        out.close();
        Timber.d("Application %s installation completed", packageName);
        session.commit(createIntentSender(context, sessionId));
        return true;
    }

    private static IntentSender createIntentSender(Context context, int sessionId) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                new Intent(ACTION_INSTALL_COMPLETE),
                0);
        return pendingIntent.getIntentSender();
    }

    @OnClick(R.id.button_remove_admin)
    public void clearDeviceAdminPermission() {
        mDpm.clearDeviceOwnerApp("com.sureshjoshi.android.kioskexample");
    }


    @Bind(R.id.webview)
    public WebView mWebView;

    private View mDecorView;
    private DevicePolicyManager mDpm;
    private boolean mIsKioskEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        ComponentName deviceAdmin = new ComponentName(this, AdminReceiver.class);
        mDpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (!mDpm.isAdminActive(deviceAdmin)) {
            Toast.makeText(getApplicationContext(), getString(R.string.not_device_admin), Toast.LENGTH_SHORT).show();
        }

        if (mDpm.isDeviceOwnerApp(getPackageName())) {
            mDpm.setLockTaskPackages(deviceAdmin, new String[]{getPackageName()});
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.not_device_owner), Toast.LENGTH_SHORT).show();
        }

        mDecorView = getWindow().getDecorView();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_INSTALL_COMPLETE);
        intentFilter.addAction(ACTION_EXIT_KIOSK_MODE);
        registerReceiver(mIntentReceiver, intentFilter);

        mWebView.loadUrl("http://www.moveinsync.com/");
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    // This snippet hides the system bars.
    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        mDecorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void enableKioskMode(boolean enabled) {
        try {
            if (enabled) {
                if (mDpm.isLockTaskPermitted(this.getPackageName())) {
                    startLockTask();
                    mIsKioskEnabled = true;
                    mButton.setText(getString(R.string.exit_kiosk_mode));
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.kiosk_not_permitted), Toast.LENGTH_SHORT).show();
                }
            } else {
                stopLockTask();
                mIsKioskEnabled = false;
                mButton.setText(getString(R.string.enter_kiosk_mode));
            }
        } catch (Exception e) {
            // TODO: Log and handle appropriately
        }
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_INSTALL_COMPLETE.equals(action)) {
                int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                Timber.d("PackageInstallerCallback: result= %s, packageName = %s", result, packageName);
                switch (result) {
                    case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                        // this should not happen in M, but will happen in L and L-MR1
                        startActivity((Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT));
                    }
                    break;
                    case PackageInstaller.STATUS_SUCCESS: {
                        Timber.d("Package %s installation complete", packageName);
                    }
                    break;
                    default: {
                        Timber.e("Install failed.");
                        return;
                    }
                }
            } else if (ACTION_EXIT_KIOSK_MODE.equals(action)) {
                enableKioskMode(false);
            }
        }
    };
}
