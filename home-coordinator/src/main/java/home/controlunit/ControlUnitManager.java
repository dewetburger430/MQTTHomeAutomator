package home.controlunit;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;

import home.common.Port;
import home.device.DeviceManager;
import util.Firebase;

public class ControlUnitManager {
    private static final Logger LOG = Logger.getLogger(ControlUnitManager.class.getName());

    private Map<String, ControlUnit> controlUnits = new ConcurrentHashMap<>(32);

    final DatabaseReference database;

    public ControlUnitManager(final DatabaseReference database) {
        this.database = database;

        // loadDeviceMapFromFirebase();

        ControlUnit cu = new ControlUnit();
        cu.database = this.database.child("controlsUnits");

        cu.name = "Wasgoedlyn";
        cu.setSources(new java.util.HashSet<Port>(){{
            add(DeviceManager.GetDeviceManager().getDeviceByTopic("front-door-light-switch").getPort("power3"));
            add(DeviceManager.GetDeviceManager().getDeviceByTopic("washing-line-relay").getPort("power"));
        }});

        DeviceManager.GetDeviceManager().getDeviceByTopic("front-door-light-switch").getPort("power3").setSources(new java.util.HashSet<Port>(){{
            add(cu);
        }});
        DeviceManager.GetDeviceManager().getDeviceByTopic("washing-line-relay").getPort("power").setSources(new java.util.HashSet<Port>(){{
            add(cu);
        }});
    }

    // public ControlUnit getControlUnit(final String key) {
    //     ControlUnit device = controlUnitMap.computeIfAbsent(name.toUpperCase(), n -> {
    //         try {
    //             return new ControlUnit();
    //         } catch (Exception e) {
    //             LOG.severe("Could not create a device");
    //             return null;
    //         }
    //     });
    //     controlUnit.setName(name);
    //     return device;
    // }

    // private void loadDeviceMapFromFirebase() {
    //     try {
    //         Map<String, Object> map = (Map<String, Object>) Firebase.readObject(database.child("lookup"));
    //         map.forEach((k, v) -> {
    //             deviceMap.computeIfAbsent(k, kk -> {
    //                 try {
    //                     return new Device(client, database, v.toString());
    //                 } catch (Exception e) {
    //                     return null;
    //                 }
    //             });
    //         });
    //     } catch (Exception e) {
    //     }
    // }
}