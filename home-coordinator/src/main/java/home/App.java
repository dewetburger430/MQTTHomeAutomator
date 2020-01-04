package home;

import java.io.FileInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import home.IOPort.Power;

/**
 * Hello world!
 */
public final class App {
    private App() {
    }

    private static Map <String, Device> deviceMap = new ConcurrentHashMap<>(32);

    /**
     * Says hello to the world.
     * 
     * @param args The arguments of the program.
     */
    public static void main(final String[] args) throws Exception {
        System.out.println("Hello World!");

        String serviceAccountFilename = System.getenv("firebaseServiceAccount");
        String firebaseDatabaseUrl = System.getenv("firebaseDatabaseUrl");

        // Firebase
        FileInputStream serviceAccount = new FileInputStream(serviceAccountFilename);

        FirebaseOptions firebaseOptions = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl(firebaseDatabaseUrl).build();

        FirebaseApp.initializeApp(firebaseOptions);

        // The app only has access as defined in the Security Rules
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("/some_resource");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String res = dataSnapshot.getValue().toString();
                System.out.println(res);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // TODO Auto-generated method stub

            }
        });

        for (int i=1; i<100; i++){
            ref.setValueAsync(i);
        }

        // MQTT

        final String clientId = UUID.randomUUID().toString();
        final IMqttClient client = new MqttClient("tcp://10.12.0.1:1883", clientId);

        final MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        client.connect(options);

        client.subscribe("#");
        client.setCallback(new MqttCallback() {

            @Override
            public void messageArrived(final String topic, final MqttMessage message) throws Exception {
                String topicParts[] = topic.split("/");
                String prefix = topicParts[0];
                String device = topicParts[1];
                String postfix = topicParts[2];
                switch (prefix.toUpperCase()) {
                    case "STAT":
                        if (postfix.toUpperCase().equals("RESULT")){
                            // ignore result state
                        } else if (postfix.toUpperCase().startsWith("POWER")) {
                            System.out.printf("MQTT PROCESSED: Port state: %s:%s %s\n", device, postfix, new String(message.getPayload()));
                            Device d = deviceMap.computeIfAbsent(device, dev -> new Device(client, dev));
                            d.getPort(postfix).updateState(new String(message.getPayload()));
                        }
                        break;
                    case "TELE":
                        if (postfix.toUpperCase().equals("STATE")){
                            System.out.printf("MQTT PROCESSED: Device state: %s %s\n", device, new String(message.getPayload()));
                        }
                        break;
                    case "CMND":
                        // ignore published command messages
                        break;
                    default:
                        System.out.printf("MQTT NOT PROCESSED: %s: %s\n", topic, new String(message.getPayload()));
                        break;
                }

            }

            @Override
            public void deliveryComplete(final IMqttDeliveryToken token) {
                // TODO Auto-generated method stub

            }

            @Override
            public void connectionLost(final Throwable cause) {
                // TODO Auto-generated method stub

            }
        });

        final Device s = new Device(client, "front-door-light-switch");
        deviceMap.putIfAbsent("front-door-light-switch", s);
        for (int i = 0; i < 10; i++) {
            for (int j = 1; j < 4; j++) {
                s.getPort("Power"+j).setPower(Power.TOGGLE);
            }
        }

        System.out.println("Press a key to exit...");
        while (true) {
            switch (System.in.read()) {
            case '1':
                s.getPort("Power1").setPower(Power.TOGGLE);
                break;
            case '2':
                s.getPort("Power2").setPower(Power.TOGGLE);
                break;
            case '3':
                s.getPort("Power3").setPower(Power.TOGGLE);
                break;
            case '\n':
                break;
            case '\r':
                break;
            default:
                client.disconnect();
                client.close();
                return;
            }
        }
    }
}
