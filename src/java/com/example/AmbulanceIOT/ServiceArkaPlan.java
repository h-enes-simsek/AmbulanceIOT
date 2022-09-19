package com.example.AmbulanceIOT;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.AmbulanceIOT.R;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import static com.example.AmbulanceIOT.App.CHANNEL_ID;


public class ServiceArkaPlan extends Service {
    //Hangi sayfadan hizmetin çağrıldığını öğrenmek için hangiButon isimli intentStringi alınacak
    //ve hangiHizmet stringine atanacak.veri, "hasta" veya "ambulans" olmalı
    String hangiHizmet;
    String HIZMET_HASTA = "hasta";
    String HIZMET_AMBULANS = "ambulans";

    Timer timer;

    //Eğitim amaçlı önerilen sunucuda publish fonksiyonu çalışmasına rağmen
    //subsribe fonksiyonu düzgün çalışmıyor, industrial.api.ubidots.com kullanılmalı
    //ayrıca mqtt:// yerine tcp:// kullanılmalı
    private static final String MQTT_SERVER = "tcp://industrial.api.ubidots.com:1883";
    MqttAndroidClient client;

    //sensörler için
    Light light;
    Accelerometer accelerometer;
    Gyroscope gyroscope;

    //sensor verilerini saklamak için
    float[] gyroData = new float[3];
    float[] ivmeData = new float[3];
    float lightData;

    //Ubidostan gelen verileri saklamak için
    float[] gyroDataGelen = new float[3];
    float[] ivmeDataGelen = new float[3];
    float lightDataGelen;
    //kontrol verisi, sadece Ubidotsan kontrol edilebiliyor.
    //-1 ise aksiyon alınmalı, normal değeri 1
    float controlUbidots = 1;

    //gps
    LocationListener locationListener;
    LocationManager locationManager;

    //gps konum verileri
    double lat;
    double lng;


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //Arka plan hizmeti ayarları
        //Hangi sayfadan hizmetin çağrıldığını öğrenmek için hangiButon isimli intentStringi alınıyor
        //hangiHizmet "hasta" veya "ambulans" olmalı
        hangiHizmet = intent.getStringExtra("hangiButon");

        Intent notificationIntent = null;

        //Notification a tıklandığında hangi sayfaya gideceğini belirleyen kod
        if(hangiHizmet.equals(HIZMET_HASTA)) notificationIntent = new Intent(this, Hasta.class);
        if(hangiHizmet.equals(HIZMET_AMBULANS)) notificationIntent = new Intent(this, Ambulans.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,notificationIntent,0);
        Notification notification = new NotificationCompat.Builder(this,CHANNEL_ID)
                .setContentTitle("Hizmet İsmi")
                .setContentText("Hizmet açıklaması")
                .setSmallIcon(R.drawable.ic_android) //notification görseli res/drawable da
                .setContentIntent(pendingIntent)
                .build();
        //notification aktive etmek için
        startForeground(1,notification);

        //debug
        //System.out.println("Calisan thread: "+Thread.currentThread().getName());


        //Eğer hizmet hasta sayfasından başlatıldıysa sensörler ve gps çağrılıyor.
        //kontrol, intent ile gelen hangiHizmet stringi sayesinde  sağlanıyor.
        if(hangiHizmet.equals(HIZMET_HASTA)){
            //gps
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    lat = location.getLatitude();
                    lng = location.getLongitude();
                    System.out.println("Latitude "+lat);
                    System.out.println("Altitude" +lng);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    //
                }
            };
            locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,0,locationListener);

            //Sensorler için listener oluşturuluyor
            light = new Light(this);
            light.setListener(new Light.Listener() {
                @Override
                public void onChange(float t) {
                    lightData = t;
                    String data = "lux: "+Float.toString(t);
                    //System.out.println(data);
                }
            });
            accelerometer = new Accelerometer(this);
            accelerometer.setListener(new Accelerometer.Listener() {
                @Override
                public void onTranslation(float tx, float ty, float tz) {
                    ivmeData[0]=tx;ivmeData[1]=ty;ivmeData[2]=tz;
                    String data = "x: "+Float.toString(tx)+"\ny: "+Float.toString(ty)+"\nz: "+Float.toString(tz);
                    //System.out.println(data);
                }
            });
            gyroscope = new Gyroscope(this);
            gyroscope.setListener(new Gyroscope.Listener() {
                @Override
                public void onRotation(float rx, float ry, float rz) {
                    gyroData[0]=rx;gyroData[1]=ry;gyroData[2]=rz;
                    String data = "x: "+Float.toString(rx)+"\ny: "+Float.toString(ry)+"\nz: "+Float.toString(rz);
                    //System.out.println(data);
                }
            });
            //sensorlerin çalışması için register edilmeli,
            //service durduğunda onDestroy kısmında unregister ediliyor.
            light.register();
            accelerometer.register();
            gyroscope.register();
        }

        //MQTT ile Ubidotsa bağlanılıyor.
        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(), MQTT_SERVER, clientId);

        //Ubidotsan verilerin alındığı kısım, ancak subscribe işlemi burda gerçekleşmiyor
        //Subscribe gerçekleşirse gelen veriler burdan alınıyor.
        client.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) { //Called when the client lost the connection to the broker
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String incomingData = new String(message.getPayload());
                //Veriler JSON objesine aktarılıyor.
                JSONObject gelenDataJSON = new JSONObject(incomingData);
                //value keyi Ubidots verileri parse ediliyor.
                float gelenDataValue = (float) gelenDataJSON.getDouble("value");
                switch (topic){
                    case "/v1.6/devices/havelsanstaj/light":
                        lightDataGelen = gelenDataValue;
                        //Sadece light için gps verisi gönderilmişti, bunlar alınıp parse ediliyor.
                        lat = (float) gelenDataJSON.getJSONObject("context").getDouble("lat");
                        lng = (float) gelenDataJSON.getJSONObject("context").getDouble("lng");
                        break;
                    case "/v1.6/devices/havelsanstaj/accelerometerx":
                        ivmeDataGelen[0] = gelenDataValue;
                        break;
                    case "/v1.6/devices/havelsanstaj/accelerometery":
                        ivmeDataGelen[1] = gelenDataValue;
                        break;
                    case "/v1.6/devices/havelsanstaj/accelerometerz":
                        ivmeDataGelen[2] = gelenDataValue;
                        break;
                    case "/v1.6/devices/havelsanstaj/gyrox":
                        gyroDataGelen[0] = gelenDataValue;
                        break;
                    case "/v1.6/devices/havelsanstaj/gyroy":
                        gyroDataGelen[1] = gelenDataValue;
                        break;
                    case "/v1.6/devices/havelsanstaj/gyroz":
                        gyroDataGelen[2] = gelenDataValue;
                        break;
                    case "/v1.6/devices/havelsanstaj/control":
                        controlUbidots = gelenDataValue;
                        //control verisi Ubidots üzerinden bağlantıyı kesmek için kullanılıyor.
                        //Buton açık iken 1, kapalı iken -1 verisi geliyor.
                        //her şey yolunda=1
                        //bağlantıyı kes=-1
                        if(controlUbidots==1)
                            Toast.makeText(getApplicationContext(),"Bağlantı server tarafından onaylandı.",Toast.LENGTH_SHORT).show();
                        else if(controlUbidots==-1){
                            //Hasta activitysindeki textview değiştiriliyor.
                            TextView textViewVeriPaylasiliyor = Hasta.textVeriPaylasiliyor;
                            textViewVeriPaylasiliyor.setText("Hizmet server tarafından durduruldu.");
                            Toast.makeText(getApplicationContext(),"Hizmet, server tarafından sonlandırıldı.",Toast.LENGTH_SHORT).show();
                            stopSelf();
                        }
                        break;
                    default:
                        Toast.makeText(getApplicationContext(),"Kaynağı bilinmeyen veri geldi.",Toast.LENGTH_SHORT).show();
                }

                if(hangiHizmet.equals(HIZMET_AMBULANS)){
                    //Gelen veriler ambulans sayfasına sayfasına gönderiliyor.
                    Intent senderIntentAmbulance = new Intent("servicetoAmbulans");
                    senderIntentAmbulance.putExtra("lightIntentChannel", lightDataGelen);
                    senderIntentAmbulance.putExtra("ivmeIntentChannel", gyroDataGelen);
                    senderIntentAmbulance.putExtra("gyroIntentChannel", ivmeDataGelen);
                    senderIntentAmbulance.putExtra("lat", lat);
                    senderIntentAmbulance.putExtra("lng", lng);
                    LocalBroadcastManager.getInstance(ServiceArkaPlan.this).sendBroadcast(senderIntentAmbulance);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {//Called when a outgoing publish is complete
            }
        });

        //Ubidotsa bağlanma kodu, client.setcallbackten sonra yazılmalı
        connect();

        //Publish sadece hasta tarafından gerçekleştirilmeli, intent ile gelen
        //hangiHizmet stringi ile kontrol gerçekleşiyor.
        if(hangiHizmet.equals(HIZMET_HASTA)) {
            //Timer belli periyotlarla çalışıyor.
            //Timer yeni bir thread oluşturuyor.
            timer = new Timer("TimerThread0");
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {

                    //debug kodları
                    //System.out.println("timer calisti");
                    //System.out.println(Thread.currentThread().getName());

                    //MQTT publish fonksiyonu
                    try {
                        publish();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    //Hasta sayfasına sensör datalarını göndermek için (ekrana yazdırmak için)
                    Intent senderIntent = new Intent("servicetoHasta");
                    senderIntent.putExtra("lightIntentChannel", lightData);
                    senderIntent.putExtra("ivmeIntentChannel", gyroData);
                    senderIntent.putExtra("gyroIntentChannel", ivmeData);
                    LocalBroadcastManager.getInstance(ServiceArkaPlan.this).sendBroadcast(senderIntent);

                }
            }, 2000, 2000);
        }

        return START_NOT_STICKY;
    }

    //Hizmet durmadan hemen önce çalışan fonksiyon
    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println("hizmet durdu");

        if(hangiHizmet.equals(HIZMET_HASTA)){
            //timer listener siliniyor
            timer.cancel();
            //sensor listenerleri siliniyor
            light.unregister();
            accelerometer.unregister();
            gyroscope.unregister();
            //gps listener silinmeli
            if(locationManager!=null){
                locationManager.removeUpdates(locationListener);
            }
        }

        //MQTT bağlantısı koparılıp, obje siliniliyor.
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
        client = null;

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void connect(){
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName("BBFF-HZXrssxWfK27B2uEzo9oD9QDzp70uG");
        options.setPassword("".toCharArray());

        try {
            IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    Toast.makeText(getApplicationContext(),"Bağlantı gerçekleşti.",Toast.LENGTH_SHORT).show();
                    //subscribe,ambulans sayfası için
                    if(hangiHizmet.equals(HIZMET_AMBULANS)){
                        try {
                            //tüm verilere subscribe için /v1.6/devices/{cihaz-ismi}
                            //tek veriye subscribe için /v1.6/devices/{cihaz-ismi}/{label}
                            //context (gps gibi) verileri değil sadece değerini
                            //almak için /v1.6/devices/{cihaz-ismi}/{label}/lv
                            client.subscribe("/v1.6/devices/havelsanstaj/light", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/accelerometerx", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/accelerometery", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/accelerometerz", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/gyrox", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/gyroy", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/gyroz", 0);
                            client.subscribe("/v1.6/devices/havelsanstaj/control", 0);

                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                        //subscribe, hasta sadece controlu dinliyor.
                    }else if(hangiHizmet.equals(HIZMET_HASTA)){
                        try {
                            client.subscribe("/v1.6/devices/havelsanstaj/control", 0);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Toast.makeText(getApplicationContext(),"Bağlantı gerçekleşemedi.",Toast.LENGTH_SHORT).show();
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish() throws JSONException {
        //debug
        System.out.println("pub thread: "+Thread.currentThread().getName());

        //Ubidots için topic: /v1.6/devices/{cihaz-ismi}
        String topic = "/v1.6/devices/havelsanstaj";

        //GPS verileri JSON a
        //lat ve lng verileri geç geldiği için bir süre
        //veri yazılamıyor ve sıfır gönderiliyor.
        JSONObject gpsJSON = new JSONObject();
        gpsJSON.put("lat",lat);
        gpsJSON.put("lng",lng);

        //Ubidotsa gönderilecek JSON
        JSONObject dataUbidots = new JSONObject();

        //Her veri tipi için ayrı ayrı JSON objesi
        //Sadece light için gps var, yeterli
        JSONObject lightDetails = new JSONObject();
        lightDetails.put("value",lightData);
        lightDetails.put("context",gpsJSON);

        JSONObject ivmeDetailsx = new JSONObject();
        JSONObject ivmeDetailsy = new JSONObject();
        JSONObject ivmeDetailsz = new JSONObject();
        ivmeDetailsx.put("value",ivmeData[0]);
        ivmeDetailsy.put("value",ivmeData[1]);
        ivmeDetailsz.put("value",ivmeData[2]);

        JSONObject gyroDetailsx = new JSONObject();
        JSONObject gyroDetailsy = new JSONObject();
        JSONObject gyroDetailsz = new JSONObject();
        gyroDetailsx.put("value",gyroData[0]);
        gyroDetailsy.put("value",gyroData[1]);
        gyroDetailsz.put("value",gyroData[2]);

        //JSON objeleri ana objeye ekleniyor.
        dataUbidots.put("light",lightDetails);
        dataUbidots.put("accelerometerx",ivmeDetailsx);
        dataUbidots.put("accelerometery",ivmeDetailsy);
        dataUbidots.put("accelerometerz",ivmeDetailsz);
        dataUbidots.put("gyrox",gyroDetailsx);
        dataUbidots.put("gyroy",gyroDetailsy);
        dataUbidots.put("gyroz",gyroDetailsz);

        //JSON to string
        String dataUbidotsString = dataUbidots.toString();

        try {
            client.publish(topic, dataUbidotsString.getBytes(),1,false);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

}
