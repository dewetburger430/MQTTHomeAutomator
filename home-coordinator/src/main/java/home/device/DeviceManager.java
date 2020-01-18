package home.device;

import java.io.Closeable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import util.Firebase;

public class DeviceManager implements Closeable {
    private static final Logger LOG = Logger.getLogger(DeviceManager.class.getName());
    private static DeviceManager instance;

    private Map<String, Device> deviceMapByTopic = new ConcurrentHashMap<>(32);
    private Map<String, Device> deviceMapByKey = new ConcurrentHashMap<>(32);

    private DatabaseReference database;
    private IMqttAsyncClient client;

    private Thread clientMonitor;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    public void initiate (final DatabaseReference database) throws MqttSecurityException, MqttException{
        this.database = database;

        this.client = configureClient();

        loadDeviceMapFromFirebase();

        subscribeToTopics();
        requestDeviceStatus();
    }
    public static DeviceManager GetDeviceManager() {
        if (instance == null){
            instance = new DeviceManager();
        }
        return instance;
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

    private IMqttAsyncClient configureClient() throws MqttSecurityException, MqttException {
        String mqttAddress = System.getenv("mqttAddress");
        String mqttPort = System.getenv("mqttPort");
        LOG.info("MQTT Server: " + mqttAddress + ":" + mqttPort);

        final String clientId = UUID.randomUUID().toString();
        final IMqttAsyncClient client = new MqttAsyncClient("tcp://" + mqttAddress + ":" + mqttPort, clientId);
        final MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(false);
        options.setConnectionTimeout(10);

        client.setCallback(new MqttCallback() {

            @Override
            public void messageArrived(final String fullTopic, final MqttMessage message) {
                LOG.finest("MQTT message arrived, processing...");
                String topicParts[] = fullTopic.split("/");
                String prefix = topicParts[0];
                String topic = topicParts[1];
                String postfix = topicParts[2];
                switch (prefix.toUpperCase()) {
                case "STAT":
                    if (postfix.toUpperCase().equals("RESULT")) {
                        // This state message is the result of a state request
                        LOG.fine(String.format("MQTT PROCESSED: Device state: %s %s", fullTopic,
                                new String(message.getPayload())));
                        Device d = getDeviceByTopic(topic);
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
                if (postfix.toUpperCase().equals("LWT")){
                    LOG.fine(String.format("MQTT PROCESSED: Device connected state: %s %s", fullTopic,
                            new String(message.getPayload())));
                    Device d = getDeviceByTopic(topic);
                    d.setConnected(new String(message.getPayload()));
                } else if (postfix.toUpperCase().equals("STATE")) {
                    // This state message is send by the devices on their accord
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
                LOG.finest("MQTT message processing complete");
            }

            @Override
            public void deliveryComplete(final IMqttDeliveryToken token) {
                try {
                    LOG.finer("MQTT message delivered: Topics = {"
                            + Stream.of(token.getTopics()).collect(Collectors.joining(", ")) + "} + message = "
                            + token.getMessage());
                } catch (Exception e) {
                }
            }

            @Override
            public void connectionLost(final Throwable cause) {
                LOG.severe("MQTT connection lost: " + cause.getMessage());
            }
        });

        client.connect(options);

        clientMonitor = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean connectedPrev = true;
                while (isRunning.get()) {
                    try {
                        Thread.sleep(1000);
                        if (!client.isConnected()) {
                            connectedPrev = false;
                            LOG.fine("Trying to reconnect to MQTT broker");
                            client.reconnect();
                        } else {
                            if (!connectedPrev) {
                                LOG.info("MQTT connection reconnected");
                            }
                            connectedPrev = true;
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }, "clientMonitor");

        clientMonitor.start();

        return client;
    }

    private void subscribeToTopics() throws MqttSecurityException, MqttException {
        client.subscribe(String.format("#"), 0);
        // TODO: It looks like the following subscription can be used
        //client.subscribe("stat/+/RESULT");
        //client.subscribe("tele/+/LWT")

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

    public void close() {
        try {
            isRunning.set(false);
            clientMonitor.join();
            LOG.info("Disconnecting MQTT client");
            client.disconnect(0);
        } catch (Exception e) {
            LOG.severe("Could not close MQTT connection");
        }
    }
}