package home.device;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.client.mqttv3.IMqttClient;

public class DeviceManager {
    private static final Logger LOG = Logger.getLogger(DeviceManager.class.getName());

    private Map<String, Device> deviceMap = new ConcurrentHashMap<>(32);

    final IMqttClient client;
    final DatabaseReference database;

    public DeviceManager(final IMqttClient client, final DatabaseReference database) {
        this.client = client;
        this.database = database;

        loadDeviceMapFromFirebase();
    }

    public Device getDevice(final String name) {
        Device device = deviceMap.computeIfAbsent(name.toUpperCase(), n -> {
            try {
                return new Device(name, client, database, null);
            } catch (Exception e) {
                LOG.severe("Could not create a device");
                return null;
            }
        });
        device.setName(name);
        return device;
    }

    private void loadDeviceMapFromFirebase() {
        final Object sync = new Object();

        database.child("lookup").addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    map.forEach((k, v) -> {
                        deviceMap.computeIfAbsent(k, kk -> {
                            try {
                                return new Device(client, database, v.toString());
                            } catch (Exception e) {
                                return null;
                            }
                        });
                    });
                } catch (Exception e) {
                } finally {
                    synchronized (sync) {
                        sync.notifyAll();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // TODO Auto-generated method stub

            }
        });
        synchronized (sync) {
            try {
                sync.wait();
            } catch (Exception e) {
            }
        }
    }
}