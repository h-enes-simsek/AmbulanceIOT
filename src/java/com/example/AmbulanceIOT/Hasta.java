package com.example.AmbulanceIOT;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.example.AmbulanceIOT.R;

public class Hasta extends AppCompatActivity {
    Intent serviceIntentHasta;
    public static TextView textVeriPaylasiliyor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hasta);
        //veri paylaşılıyor yazan metin.
        textVeriPaylasiliyor = findViewById(R.id.textViewVeriPaylasiliyor);
    }


    //service başlatıcı, butona bağlı
    public void startServiceButton(View v){
        serviceIntentHasta = new Intent(this,ServiceArkaPlan.class);
        //hangi butona basıldığını service de öğrenilmek için kontrol verisi gönderiliyor.
        serviceIntentHasta.putExtra("hangiButon","hasta");
        startService(serviceIntentHasta);

        textVeriPaylasiliyor.setText("Verileriniz paylaşılıyor.");

    }

    //service durdurucu, butona bağlı
    public void stopServiceButton(View v){
        serviceIntentHasta = new Intent(this,ServiceArkaPlan.class);
        stopService(serviceIntentHasta);
        serviceIntentHasta=null;
        textVeriPaylasiliyor.setText("Hizmet durduruldu.");
    }


    //geri tuşuna basıldığını algılaması ve hizmeti durdurması için
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){

            //Eğer hizmet başlatılmışsa geri tuşuna basıldığında soru sor
            if(isMyServiceRunning(ServiceArkaPlan.class)){
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
                                        stopService(serviceIntentHasta);
                                        //ve bir sayfa geri git.
                                        finish();
                                        //textview
                                        textVeriPaylasiliyor.setText("Hizmet durduruldu.");
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