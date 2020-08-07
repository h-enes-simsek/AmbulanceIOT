package com.example.AmbulanceIOT;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.AmbulanceIOT.R;

public class Anasayfa extends AppCompatActivity {

    public Button ambulansButon;
    public Button hastaButon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anasayfa);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
        }
        else {
            ActivityCompat.requestPermissions(this, new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION },
                    1340);
        }

        ambulansButon=findViewById(R.id.btn_ambulans);
        hastaButon =findViewById(R.id.btn_yarali);

        ambulansButon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Ammbulans sayfasına yönlendiren buton
                Intent ambulanceIntent=new Intent(Anasayfa.this, Ambulans.class);
                startActivity(ambulanceIntent);

            }
        });

        hastaButon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Hasta sayfasına yönlendiren buton
                Intent hastaIntent=new Intent(Anasayfa.this, Hasta.class);
                startActivity(hastaIntent);
            }
        });

    }

}