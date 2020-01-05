package home.device;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import util.Firebase;

public class DeviceManager {
    private static final Logger LOG = Logger.getLogger(DeviceManager.class.getName());

    private Map<String, Device> deviceMapByTopic = new ConcurrentHashMap<>(32);
    private Map<String, Device> deviceMapByKey = new ConcurrentHashMap<>(32);

    final DatabaseReference database;
    final IMqttClient client;

    public DeviceManager(final DatabaseReference database) throws MqttSecurityException, MqttException {
        this.database = database;

        this.client = configureClient();

        loadDeviceMapFromFirebase();
        requestDeviceStatus();
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

    private MqttClient configureClient() throws MqttSecurityException, MqttException {
        String mqttAddress = System.getenv("mqttAddress");
        String mqttPort = System.getenv("mqttPort");
        LOG.info("MQTT Server: " + mqttAddress + ":" + mqttPort);

        final String clientId = UUID.randomUUID().toString();
        final MqttClient client = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, clientId);
        final MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        client.setCallback(new MqttCallback() {

            @Override
            public void messageArrived(final String fullTopic, final MqttMessage message) throws Exception {
                String topicParts[] = fullTopic.split("/");
                String prefix = topicParts[0];
                String topic = topicParts[1];
                String postfix = topicParts[2];
                switch (prefix.toUpperCase()) {
                case "STAT":
                    Device d = getDeviceByTopic(topic);
                    if (postfix.toUpperCase().equals("RESULT")) {
                        // This state message is the result of a state request
                        LOG.fine(String.format("MQTT PROCESSED: Device state: %s %s", fullTopic,
                                new String(message.getPayload())));
                        d.updateStatus(new String(message.getPayload()));
                    // } else if (postfix.toUpperCase().startsWith("POWER")) {
                    //     LOG.fine(String.format("MQTT PROCESSED: Port state: %s:%s", fullTopic,
                    //             new String(message.getPayload())));
                    //     d.getPort(postfix).setState(new String(message.getPayload()));
                    } else {
                        LOG.finest(String.format("MQTT IGNORE MESSAGE: %s: %s", fullTopic, new String(message.getPayload())));
                    }
                    break;
                case "TELE":
                    // This state message is send by the devices on their accord
                    if (postfix.toUpperCase().equals("STATE")) {
                        LOG.finest(String.format("MQTT IGNORE MESSAGE: (DEVICE CONTROLLED STATE) %s: %s", fullTopic, new String(message.getPayload())));
                        // LOG.fine(String.format("MQTT PROCESSED: Device state: %s %s", fullTopic,
                        //         new String(message.getPayload())));
                        // d.updateStatus(new String(message.getPayload()));
                    } else {
                        LOG.finest(String.format("MQTT IGNORE MESSAGE: %s: %s", fullTopic, new String(message.getPayload())));
                    }
                    break;
                case "CMND":
                    LOG.finest(String.format("MQTT IGNORE MESSAGE: %s: %s", fullTopic, new String(message.getPayload())));
                    break;
                default:
                    LOG.warning(String.format("MQTT NOT PROCESSED: %s: %s", fullTopic, new String(message.getPayload())));
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

        client.connect(options);
        client.subscribe(String.format("#"));
        // TODO: It looks like the following subscription can be used
        //client.subscribe("STAT/+/RESULT");

        return client;
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

    public void close(){
        deviceMapByTopic.values().forEach(d -> d.close());
    }
}