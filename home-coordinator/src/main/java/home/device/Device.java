package home.device;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.google.firebase.database.DatabaseReference;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import util.Firebase;

public class Device {

    private static final Logger LOG = Logger.getLogger(Device.class.getName());

    private final IMqttClient client;
    private final String firebaseId;
    private String topic;
    private final DatabaseReference database;

    private final Map<String, IOPort> ports = new TreeMap<>();

    protected Device(final IMqttClient client, DatabaseReference database, String firebaseId) {
        this(null, client, database, firebaseId);
    }

    protected Device(final String topic, final IMqttClient client, DatabaseReference database) {
        this(topic, client, database, null);
    }

    protected Device(final String topic, final IMqttClient client, final DatabaseReference database,
            final String firebaseId) {
        LOG.fine("Constructing new device: " + topic);
        this.client = client;
        this.topic = topic;

        if (firebaseId == null) {
            this.firebaseId = getOrAllocateFirebaseId(database);
        } else {
            this.firebaseId = firebaseId;
        }

        LOG.finer("Update device in firebase");
        this.database = database.child("list").child(this.firebaseId);
        this.database.updateChildrenAsync(java.util.Collections.singletonMap("topic", topic));
    }

    private String getOrAllocateFirebaseId(DatabaseReference database) {
        DatabaseReference lookupPath = database.child("lookup").child(topic.toUpperCase());
        LOG.finer("Looking up firebaseId for device: " + lookupPath.getPath());

        Object firebaseId = Firebase.readObject(lookupPath);

        if (firebaseId == null) {
            // Generate new key
            firebaseId = database.push().getKey();
            LOG.finer("Creating new device in firebase with key: " + firebaseId);
            lookupPath.setValueAsync(firebaseId);
        }

        return firebaseId.toString();
    }

    public IOPort getPort(String name) {
        IOPort port = ports.get(name);
        if (port != null) {
            return port;
        }
        synchronized (ports) {
            return ports.computeIfAbsent(name.toUpperCase(), n -> new IOPort(this, name, this.database.child("ports")));
        }
    }

    protected void send(final String postfix, final String message) throws Exception {
        if (!client.isConnected()) {
        }
        MqttMessage msg = new MqttMessage(message.getBytes());
        msg.setQos(0);
        msg.setRetained(true);
        String fullTopic = String.format("cmnd/%s/%s", this.topic, postfix);
        LOG.finer("Publish MQTT message: " + fullTopic + " " + message);
        client.publish(fullTopic, msg);
    }

    public String getName() {
        return topic;
    }

    public void setTopic(String topic) {
        if (this.topic == null) {
            this.database.updateChildrenAsync(java.util.Collections.singletonMap("topic", topic));
        }
        this.topic = topic;
    }

    public String getFirebaseId() {
        return firebaseId;
    }
}
