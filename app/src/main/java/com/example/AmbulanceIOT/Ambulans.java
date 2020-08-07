package com.example.AmbulanceIOT;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.example.AmbulanceIOT.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

//fectlastlocation() çok gerekli değil, gps verileri başka şekilde alınıyor, kodlar düzenlenip silinebilir.

public class Ambulans extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap map; //map i kontrol eden değişken
    Marker mapMarker; //harita üzerindeki işaret

    Intent serviceIntentAmbulans;

    Location currentLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    private static final int REQUEST_CODE = 101;

    //sensör verileri
    float[] gyroData = new float[3];
    float[] ivmeData = new float[3];
    float lightData;
    //gps verileri
    double lat,lng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ambulans);

        //Serviceden alınacak sensör verileri için intent register
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("servicetoAmbulans"));

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fetcLastLocation();
    }

    //Serviceden gelecek sensör verilerini dinlemek için
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Verileri intent ile al
            //lightdata için defaultvalue, eğer veri gelmezse alınacak değer.
            lightData = intent.getFloatExtra("lightIntentChannel",-1);
            ivmeData = intent.getFloatArrayExtra("ivmeIntentChannel");
            gyroData = intent.getFloatArrayExtra("gyroIntentChannel");
            lat = intent.getDoubleExtra("lat",0);
            lng = intent.getDoubleExtra("lng",0);

            //Floatlar stringe dönüştürülüyor.
            String ivmeDataString = "x: "+Float.toString(ivmeData[0])
                    +"\ny: "+Float.toString(ivmeData[1])
                    +"\nz: "+Float.toString(ivmeData[2]);
            String gyroDataString = "x: "+Float.toString(gyroData[0])
                    +"\ny: "+Float.toString(gyroData[1])
                    +"\nz: "+Float.toString(gyroData[2]);
            String lightDataString = Float.toString(lightData);

            //textviewlara sensör verileri yazdırılıyor.
            final TextView ivmeTextView = (TextView) Ambulans.this.findViewById(R.id.textViewIvme);
            final TextView gyroTextView = (TextView) Ambulans.this.findViewById(R.id.textViewGyro);
            final TextView lightTextView = (TextView) Ambulans.this.findViewById(R.id.textViewLight);
            final TextView GPSTextView = (TextView) Ambulans.this.findViewById(R.id.textViewGPS);

            GPSTextView.setText("lat: "+lat+"\nlng: "+lng);
            ivmeTextView.setText(ivmeDataString);
            gyroTextView.setText(gyroDataString);
            lightTextView.setText(lightDataString);

            //BUG harita sürekli güncelleniyor, yeni bir intent ile sdsace gps verileri gönderilebilir.
            //harita güncelleme
            //önceki marker siliniyor.
            if(mapMarker!=null) mapMarker.remove();
            LatLng latLng=new LatLng(lat,lng);
            MarkerOptions markerOptions=new MarkerOptions().position(latLng).title("Başka nokta");
            map.animateCamera(CameraUpdateFactory.newLatLng(latLng));
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,15));
            mapMarker = map.addMarker(markerOptions);
        }
    };

    private void fetcLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE);
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(location != null){
                    currentLocation=location;
                    SupportMapFragment supportMapFragment=(SupportMapFragment)
                            getSupportFragmentManager().findFragmentById(R.id.google_map);
                    supportMapFragment.getMapAsync(Ambulans.this);
                }

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        //harita hazır olduğunda global scopedaki map değişkenine atanıyor.
        map=googleMap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case REQUEST_CODE:
                if (grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    fetcLastLocation();
                }
                break;
        }
    }
    //service başlatıcı, butona bağlı
    public void startServiceButton(View v){
        serviceIntentAmbulans = new Intent(this,ServiceArkaPlan.class);
        //hangi butona basıldığını service de öğrenilmek için kontrol verisi gönderiliyor.
        serviceIntentAmbulans.putExtra("hangiButon","ambulans");
        serviceIntentAmbulans.setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startService(serviceIntentAmbulans);
    }

    //service durdurucu, butona bağlı
    public void stopServiceButton(View v){
        serviceIntentAmbulans = new Intent(this,ServiceArkaPlan.class);
        stopService(serviceIntentAmbulans);
        serviceIntentAmbulans = null;
    }
    //geri tuşuna basıldığını algılaması ve hizmeti durdurması için
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){

            //Eğer hizmet başlatılmışsa geri tuşuna basıldığında soru sor
            if(isMyServiceRunning(Ambulans.class)){
                // alertdialog for exit the app
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

                // set the title of the Alert Dialog
                alertDialogBuilder.setTitle("your title");

                // set dialog message
                alertDialogBuilder
                        .setMessage("Bağlantıyı kesmek istiyor musunuz?")
                        .setCancelable(false)
                        .setPositiveButton("Evet",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int id) {
                                        //Hizmeti durdur.
                                        stopService(serviceIntentAmbulans);
                                        //ve bir sayfa geri git.
                                        finish();
                                    }
                                })

                        .setNeutralButton("İptal",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int id) {
                                        // bir şey yapma
                                        dialog.cancel();
                                    }
                                })

                        .setNegativeButton("Hayır",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int id) {
                                        // bir şey yapma
                                        dialog.cancel();
                                    }
                                });

                AlertDialog alertDialog = alertDialogBuilder.create();

                alertDialog.show();
            }

        }
        return super.onKeyDown(keyCode, event);
    }
    //Arka plan hizmeti sürüyor mu kontrol etmek için
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}