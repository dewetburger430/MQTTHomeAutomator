package home;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Device{
 
    private final IMqttClient client;
    private final String topic;

    private final Map<String, IOPort> portMap = new HashMap<>(4);
     
    public Device(final IMqttClient client, final String topic) {
        this.client = client;
        this.topic = topic;
    }
 
    public IOPort getPort(String name){
        return portMap.computeIfAbsent(name, n -> new IOPort(this, n));
    }

    protected void send(final String name, final String message) throws Exception{
        if ( !client.isConnected()) {
        }           
        MqttMessage msg = new MqttMessage(message.getBytes());
        msg.setQos(0);
        msg.setRetained(true);
        String fullTopic = String.format("cmnd/%s/%s", topic, name);
        client.publish(fullTopic, msg);        
    }
}
