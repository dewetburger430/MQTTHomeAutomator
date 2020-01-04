package home.device;

import java.util.logging.Logger;

import com.google.firebase.database.DatabaseReference;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

public class IOPort {
    private static final Logger LOG = Logger.getLogger(IOPort.class.getName());

    private final Device device;
    private final String name;
    private final DatabaseReference database;

    private State state = State.UNKNOWN;

    public enum State {
        UNKNOWN, ON, OFF,
    }

    public enum Power {
        ON, OFF, TOGGLE,
    }

    protected IOPort(final Device device, final String name, final DatabaseReference database) {
        this.device = device;
        this.name = name;
        this.database = database.child(name.toUpperCase());
        this.database.setValueAsync(this);
    }

    public void setPower(final String p) throws MqttPersistenceException, MqttException {
        setPower(Power.valueOf(p));
    }
    
    public void setPower(final Power p) throws MqttPersistenceException, MqttException {
        LOG.finer("Set IOPort power: " + p.toString());
        device.send(name, p.toString());
    }

    public void setState(final String state) {
        LOG.finer("Set IOPort state: " + state.toString());
        try {
            final State ps = this.state;
            this.state = State.valueOf(state.toUpperCase());
            if (ps != this.state) {
                this.database.setValueAsync(this);
            }
        } catch (final Exception e) {
            LOG.warning("Could not update state");
            this.state = State.UNKNOWN;
        }
    }

    public State getState() {
        return state;
    }
}
