package home.device;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.firebase.database.DatabaseReference;

import org.eclipse.paho.client.mqttv3.IMqttClient;

import util.Firebase;

public class DeviceManager {
    private static final Logger LOG = Logger.getLogger(DeviceManager.class.getName());

    private Map<String, Device> deviceMapByTopic = new ConcurrentHashMap<>(32);
    private Map<String, Device> deviceMapByKey = new ConcurrentHashMap<>(32);

    final IMqttClient client;
    final DatabaseReference database;

    public DeviceManager(final IMqttClient client, final DatabaseReference database) {
        this.client = client;
        this.database = database;

        loadDeviceMapFromFirebase();
    }

    public Device getDeviceByTopic(final String topic) {
        Device device = deviceMapByTopic.computeIfAbsent(topic.toUpperCase(),
                key -> new Device(topic, client, database, null));
        deviceMapByKey.putIfAbsent(device.getFirebaseId(), device);
        device.setTopic(topic);
        return device;
    }

    public Device getDeviceByKey(final String key) {
        return deviceMapByKey.get(key);
    }

    private void loadDeviceMapFromFirebase() {
        LOG.fine("Load devices from firebase");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) Firebase.readObject(database.child("lookup"));
            map.forEach((k, v) -> {
                Device device = deviceMapByTopic.computeIfAbsent(k, kk -> new Device(client, database, v.toString()));
                deviceMapByKey.putIfAbsent(device.getFirebaseId(), device);
            });
        } catch (Exception e) {
        }
    }
}