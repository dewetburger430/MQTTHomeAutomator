package home.device;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Device {

    private static final Logger LOG = Logger.getLogger(Device.class.getName());

    private final IMqttClient client;
    private final String firebaseId;
    private String name;
    private final DatabaseReference database;

    private final Map<String, IOPort> ports = new HashMap<>(4);

    protected Device(final IMqttClient client, DatabaseReference database, String firebaseId)
            throws InterruptedException {
        this(null, client, database, firebaseId);
    }

    protected Device(final String name, final IMqttClient client, DatabaseReference database)
            throws InterruptedException {
        this(name, client, database, null);
    }

    protected Device(final String name, final IMqttClient client, final DatabaseReference database,
            final String firebaseId) throws InterruptedException {
        LOG.fine("Constructing new device: " + name);
        this.client = client;
        this.name = name;

        if (firebaseId == null) {
            this.firebaseId = getOrAllocateFirebaseId(database);
        } else {
            this.firebaseId = firebaseId;
        }

        LOG.finer("Update device in firebase");
        this.database = database.child("list").child(this.firebaseId);
        this.database.updateChildrenAsync(new HashMap<String, Object>() {
            {
                if (name != null) {
                    put("name", name);
                }
            }
        });
    }

    private String getOrAllocateFirebaseId(DatabaseReference database) throws InterruptedException {
        final Object sync = new Object();
        final String[] id = new String[1];

        DatabaseReference lookupPath = database.child("lookup").child(name.toUpperCase());
        LOG.finer("Looking up firebaseId for device: " + lookupPath.getPath());
        lookupPath.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                LOG.finer("Retrieved firebaseId: " + snapshot.getValue());
                if (snapshot.getValue() != null) {
                    if (!snapshot.getValue().toString().equals("null")) {
                        id[0] = snapshot.getValue().toString();
                    }
                }
                synchronized (sync) {
                    sync.notifyAll();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        });

        synchronized (sync) {
            sync.wait();
        }

        if (id[0] == null) {
            // Generate new key
            id[0] = database.push().getKey();
            LOG.finer("Creating new device in firebase with key: " + id[0]);
            lookupPath.setValueAsync(id[0]);
        }

        return id[0];
    }

    public IOPort getPort(String name) {
        return ports.computeIfAbsent(name.toUpperCase(), n -> new IOPort(this, name, this.database.child("ports")));
    }

    protected void send(final String postfix, final String message) throws Exception {
        if (!client.isConnected()) {
        }
        MqttMessage msg = new MqttMessage(message.getBytes());
        msg.setQos(0);
        msg.setRetained(true);
        String fullTopic = String.format("cmnd/%s/%s", this.name, postfix);
        LOG.finer("Publish MQTT message: " + fullTopic + " " + message);
        client.publish(fullTopic, msg);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirebaseId() {
        return firebaseId;
    }
}
