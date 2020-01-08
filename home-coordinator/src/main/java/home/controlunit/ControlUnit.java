package home.controlunit;

import com.google.firebase.database.DatabaseReference;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import home.common.Port;
import home.common.Power;
import home.common.State;

public class ControlUnit extends Port {
    protected DatabaseReference database;

    public enum Type {
        EVENT,
    }

    public Type type;

    public ControlUnit() {
        this.database = null;
    }

}