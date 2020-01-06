package home;

import java.io.FileInputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

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

        // Overall contoller
        DatabaseReference controllerRef = FirebaseDatabase.getInstance().getReference("/controller");
        controllerRef.child("connected").setValueAsync("ONLINE");
        controllerRef.child("connected").onDisconnect().setValueAsync("OFFLINE");
        controllerRef.child("connectedChanged").setValueAsync(ServerValue.TIMESTAMP);
        controllerRef.child("connectedChanged").onDisconnect().setValueAsync(ServerValue.TIMESTAMP);

        // Device controller
        DatabaseReference deviceRef = FirebaseDatabase.getInstance().getReference("/devices");
        DeviceManager deviceManager = new DeviceManager(deviceRef);

        System.out.println("Press enter to exit...");
        System.in.read();
        deviceManager.close();
    }
}
