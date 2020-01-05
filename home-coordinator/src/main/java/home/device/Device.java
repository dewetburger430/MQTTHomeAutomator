package home.device;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.gson.Gson;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import util.Firebase;

public class Device {

    private static final Logger LOG = Logger.getLogger(Device.class.getName());

    @Exclude
    private final IMqttClient client;
    @Exclude
    private final DatabaseReference database;
    @Exclude
    private final Map<String, IOPort> ports = new TreeMap<>();
    @Exclude
    private final String firebaseId;

    private String topic;

    public Device() {
        this.client = null;
        this.database = null;
        this.firebaseId = null;
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

        DataSnapshot firebaseIdSnapshot = Firebase.readObject(lookupPath);
        String firebaseId;

        if (firebaseIdSnapshot == null) {
            // Generate new key
            firebaseId = database.push().getKey();
            LOG.finer("Creating new device in firebase with key: " + firebaseId);
            lookupPath.setValueAsync(firebaseId);
        } else {
            firebaseId = firebaseIdSnapshot.getValue().toString();
        }

        return firebaseId.toString();
    }

    public IOPort getPort(String name) {
        name = name.toUpperCase();
        IOPort port = ports.get(name);
        if (port != null) {
            return port;
        }
        synchronized (ports) {
            return ports.computeIfAbsent(name, n -> new IOPort(this, n, this.database.child("ports")));
        }
    }

    protected void send(final String postfix, final String message) throws MqttPersistenceException, MqttException {
        if (!client.isConnected()) {
        }
        MqttMessage msg = new MqttMessage(message.getBytes());
        msg.setQos(0);
        msg.setRetained(true);
        String fullTopic = String.format("cmnd/%s/%s", this.topic, postfix);
        LOG.finer("Publish MQTT message: " + fullTopic + " " + message);
        client.publish(fullTopic, msg);
    }

    protected void requestStatus() throws MqttPersistenceException, MqttException {
        String fullTopic = String.format("cmnd/%s/STATE", this.topic);
        MqttMessage msg = new MqttMessage("".getBytes());
        msg.setQos(0);
        msg.setRetained(true);
        client.publish(fullTopic, msg);
    }

    protected void updateStatus(String statusMessage) {
        Gson gson = new Gson();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) gson.fromJson(statusMessage, Map.class);
        LOG.finer("Status message parsed successfully");
        map.forEach((k, v) -> {
            if (k.toUpperCase().startsWith("POWER")) {
                this.getPort(k).setState(v.toString());
            }
            if (k.toUpperCase().equals("WIFI")) {
                LOG.finest("Process wifi status: " + v.toString());
                @SuppressWarnings("unchecked")
                Map<String, Object> wifiMap = (Map<String, Object>) v;
                LOG.finest("Wifi map: " + wifiMap);
                Object signal = wifiMap.get("Signal");
                if (signal != null) {
                    try {
                        database.child("wifi").child("signal").setValueAsync(signal);
                        long now = Instant.now().getEpochSecond();
                        now = now - (now % (15 * 60));
                        database.getRoot().child("stats").child("wifi").child(firebaseId).child(String.valueOf(now))
                                .setValueAsync(signal);
                    } catch (Exception e) {
                        LOG.warning("Could not publish signal strength: " + e.getMessage());
                    }
                }
            }
        });
    }

    protected void close() {
        try{
            client.disconnect();
            client.close();
        } catch(Exception e){

        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getFirebaseId() {
        return firebaseId;
    }
}
