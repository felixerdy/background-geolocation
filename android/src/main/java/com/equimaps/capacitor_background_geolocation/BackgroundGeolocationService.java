package com.equimaps.capacitor_background_geolocation;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;

import de.fh.muenster.locationprivacytoolkit.LocationPrivacyToolkit;

import com.getcapacitor.Logger;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashSet;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// A bound and started service that is promoted to a foreground service when
// location updates have been requested and the main activity is stopped.
//
// When an activity is bound to this service, frequent location updates are
// permitted. When the activity is removed from the foreground, the service
// promotes itself to a foreground service, and location updates continue. When
// the activity comes back to the foreground, the foreground service stops, and
// the notification associated with that service is removed.
public class BackgroundGeolocationService extends Service {
    static final String ACTION_BROADCAST = (
            BackgroundGeolocationService.class.getPackage().getName() + ".broadcast"
    );
    private final IBinder binder = new LocalBinder();

    // Must be unique for this application.
    private static final int NOTIFICATION_ID = 28351;

    private LocationManager locationManager;
    private Criteria criteria;
    private boolean isStarted = false;
    private LocationPrivacyToolkit mLocationListener;
    private LocationPrivacyToolkit mExternalLocationListener;

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Location criteria
        criteria = new Criteria();
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(true);
        criteria.setCostAllowed(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private class Watcher {
        public String id;
        public FusedLocationProviderClient client;
        public LocationRequest locationRequest;
        public LocationCallback locationCallback;
        public Notification backgroundNotification;
    }

    private HashSet<Watcher> watchers = new HashSet<Watcher>();

    Notification getNotification() {
        for (Watcher watcher : watchers) {
            if (watcher.backgroundNotification != null) {
                return watcher.backgroundNotification;
            }
        }
        return null;
    }

    // Handles requests from the activity.
    public class LocalBinder extends Binder {
        void addWatcher(
                final String id,
                Notification backgroundNotification,
                float distanceFilter
        ) {
            Context context = getApplicationContext();

            mLocationListener = new LocationPrivacyToolkit(context, null) {
                @Override
                public void onLocationChanged(final Location location) {
                    Logger.debug("Location received");
                    Intent intent = new Intent(ACTION_BROADCAST);
                    intent.putExtra("location", mLocationListener.processLocation(location));
                    intent.putExtra("id", id);
                    LocalBroadcastManager.getInstance(
                            getApplicationContext()
                    ).sendBroadcast(intent);
                }

                @Override
                public void onProviderDisabled(String provider) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }
            };


            Logger.debug("Google Play Services not available, using Android location APIs");
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.requestLocationUpdates(locationManager.getBestProvider(criteria, true), 1000, distanceFilter, mLocationListener);

        }

        void removeWatcher(String id) {
            int gmsResultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
            if (gmsResultCode == ConnectionResult.SUCCESS) {
                for (Watcher watcher : watchers) {
                    if (watcher.id.equals(id)) {
                        watcher.client.removeLocationUpdates(watcher.locationCallback);
                        watchers.remove(watcher);
                        if (getNotification() == null) {
                            stopForeground(true);
                        }
                        return;
                    }
                }
            } else {
                Logger.debug("Location Listener removed");
                locationManager.removeUpdates(mLocationListener);
                if (getNotification() == null) {
                    stopForeground(true);
                }
                return;
            }
        }

        void onPermissionsGranted() {
            // If permissions were granted while the app was in the background, for example in
            // the Settings app, the watchers need restarting.
            for (Watcher watcher : watchers) {
                watcher.client.removeLocationUpdates(watcher.locationCallback);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                watcher.client.requestLocationUpdates(
                        watcher.locationRequest,
                        watcher.locationCallback,
                        null
                );
            }
        }

        void onActivityStarted() {
            stopForeground(true);
        }

        void onActivityStopped() {
            Notification notification = getNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
            }
        }

        void stopService() {
            BackgroundGeolocationService.this.stopSelf();
        }

        Location processLocation(Location location) {
            if(mExternalLocationListener == null) {
                mExternalLocationListener = new LocationPrivacyToolkit(context, null);
            }
            return mExternalLocationListener.processLocation(location);
        }
    }
}