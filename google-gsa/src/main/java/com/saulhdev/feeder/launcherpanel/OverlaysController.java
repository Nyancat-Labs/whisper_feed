package com.saulhdev.feeder.launcherpanel;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.SparseArray;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class OverlaysController {

    private final Service service;
    private final SparseArray<OverlayControllerBinder> clientBinders = new SparseArray<>();
    public final Handler handler = new Handler();

    protected OverlaysController(Service service) {
        this.service = Objects.requireNonNull(service);
    }

    public abstract OverlayController createController(Configuration configuration, int version, int clientVersion);

    public final synchronized IBinder onBind(Intent intent) {
        Uri data = intent.getData();
        if (data == null) return null;

        int port = data.getPort();
        if (port == -1) return null;

        int version = parseQueryParam(data, "v", Integer.MAX_VALUE);
        int clientVersion = parseQueryParam(data, "cv", Integer.MAX_VALUE);

        String host = data.getHost();
        List<String> packagesForUid = getPackagesForUid(port);

        if (host == null || packagesForUid == null || !packagesForUid.contains(host)) {
            return null;
        }

        OverlayControllerBinder binder = clientBinders.get(port);

        if (binder != null && binder.getServerVersion() != version) {
            binder.destroy();
            binder = null;
        }

        if (binder == null) {
            binder = new OverlayControllerBinder(this, port, host, version, clientVersion);
            clientBinders.put(port, binder);
        }

        return binder;
    }

    public final synchronized void onUnbind(Intent intent) {
        Uri data = intent.getData();
        if (data == null) return;

        int port = data.getPort();
        if (port != -1) {
            OverlayControllerBinder binder = clientBinders.get(port);
            if (binder != null) {
                binder.destroy();
            }
            clientBinders.remove(port);
        }
    }

    public final synchronized void onDestroy() {
        for (int i = clientBinders.size() - 1; i >= 0; i--) {
            OverlayControllerBinder binder = clientBinders.valueAt(i);
            if (binder != null) {
                binder.destroy();
            }
        }
        clientBinders.clear();
    }

    public int getDefaultVersion() {
        return 24;
    }

    private int parseQueryParam(Uri uri, String key, int defaultValue) {
        try {
            return Integer.parseInt(uri.getQueryParameter(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private List<String> getPackagesForUid(int uid) {
        String[] packages = service.getPackageManager().getPackagesForUid(uid);
        return packages != null ? Arrays.asList(packages) : null;
    }
}
