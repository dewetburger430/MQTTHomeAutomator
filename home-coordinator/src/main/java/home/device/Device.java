package home.device;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Device{ 
    private static Map <String, Device> deviceMap = new ConcurrentHashMap<>(32);

    public static Device getDevice(final String name, final IMqttClient client){
        return deviceMap.computeIfAbsent(name.toUpperCase(), n -> new Device(name, client));
    }
 

    private final IMqttClient client;
    private final String name;

    private final Map<String, IOPort> portMap = new HashMap<>(4);
    
    protected Device(final String name, final IMqttClient client) {
        this.client = client;
        this.name = name;
    }

    public IOPort getPort(String name){
        return portMap.computeIfAbsent(name.toUpperCase(), n -> new IOPort(this, name));
    }

    protected void send(final String postfix, final String message) throws Exception{
        if ( !client.isConnected()) {
        }           
        MqttMessage msg = new MqttMessage(message.getBytes());
        msg.setQos(0);
        msg.setRetained(true);
        String fullTopic = String.format("cmnd/%s/%s", this.name, postfix);
        client.publish(fullTopic, msg);        
    }
}
