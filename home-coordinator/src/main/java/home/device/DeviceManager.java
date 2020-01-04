package home.device;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;

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
            DataSnapshot snapshot = Firebase.readObject(database.child("list"));
            LOG.finer("Convert data to device map");
            Map<String, Device> map = snapshot.getValue(new GenericTypeIndicator<Map<String, Device>>(){});
            LOG.finer("Build structures");
            map.forEach((k, d) -> {
                LOG.finer("Processing device: " + k + ": " + d);
                LOG.finer("Add device to key map with key: " + k);
                Device device = deviceMapByKey.computeIfAbsent(k, kk -> new Device(d.getTopic(), client, database, kk));
                LOG.finer("Add device to topic map with key: " + device.getTopic().toUpperCase());
                deviceMapByTopic.putIfAbsent(device.getTopic().toUpperCase(), device);
            });
        } catch (Exception e) {
            LOG.warning("Could not read devices from firebase: " + e.getMessage());
        }
    }

    public void requestDeviceStatus() {
        deviceMapByTopic.entrySet().iterator().forEachRemaining(e -> {
            try {
                e.getValue().requestStatus();
            } catch (Exception ee) {
                LOG.warning("Could not request device status: " + ee.getMessage());
            }
        });
    }
}