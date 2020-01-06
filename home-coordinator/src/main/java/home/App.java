package home;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import home.device.IOPort.Power;
import home.device.Device;
import home.device.DeviceManager;

/**
 * Hello world!
 */
public final class App {
    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private App() {
    }

    /**
     * Says hello to the world.
     * 
     * @param args The arguments of the program.
     */
    public static void main(final String[] args) throws Exception {
        System.setProperty("java.util.logging.config.file", "home-coordinator/logging.properties");
        LogManager.getLogManager().readConfiguration();

        String serviceAccountFilename = System.getenv("firebaseServiceAccount");
        String firebaseDatabaseUrl = System.getenv("firebaseDatabaseUrl");

        LOG.info("Firebase service account file: " + serviceAccountFilename);
        LOG.info("Firebase database URL: " + firebaseDatabaseUrl);

        // Firebase
        FileInputStream serviceAccount = new FileInputStream(serviceAccountFilename);

        FirebaseOptions firebaseOptions = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount)).setDatabaseUrl(firebaseDatabaseUrl)
                .build();

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

        DatabaseReference deviceRef = FirebaseDatabase.getInstance().getReference("/devices");

        // MQTT
        String mqttAddress = System.getenv("mqttAddress");
        String mqttPort = System.getenv("mqttPort");
        LOG.info("MQTT Server: " + mqttAddress + ":" + mqttPort);

        DeviceManager deviceManager = new DeviceManager(deviceRef);

        final Device s = deviceManager.getDeviceByTopic("front-door-light-switch");

        System.out.println("Press 1,2,3 to toggle switch, anything else to exit...");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String command = br.readLine();
            switch (command) {
            case "1":
                s.getPort("Power1").setPower(Power.TOGGLE);
                break;
            case "2":
                s.getPort("Power2").setPower(Power.TOGGLE);
                break;
            case "3":
                s.getPort("Power3").setPower(Power.TOGGLE);
                break;
            default:
                LOG.info("Closing connections");
                br.close();
                deviceManager.close();
                return;
            }
        }
    }
}
