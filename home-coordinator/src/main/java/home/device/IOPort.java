package home.device;

import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Logger;

import com.google.firebase.database.DatabaseReference;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import home.common.Port;
import home.common.Power;
import home.common.State;

public class IOPort extends Port {
    private static final Logger LOG = Logger.getLogger(IOPort.class.getName());

    private final Device device;

    private int setStateCount = 0;
    private int setStateCountTarget = 0;

    protected IOPort(final Device device, final String name, final DatabaseReference database) {
        LOG.finer("Construction IOPort " + device.getTopic() + ":" + name);
        this.device = device;
        this.name = name;
        this.database = database.child(name.toUpperCase());
        this.database.child("state").setValueAsync(this.state);

        try{
            this.device.send("SetOption13", "1");
            int timezoneSeconds = TimeZone.getDefault().getRawOffset() / 1000;
            int timezoneHours = Math.abs(timezoneSeconds) / 3600;
            int timezoneMinutes = (Math.abs(timezoneSeconds) / 60) % 60;
            String timezone = String.format("%s%02d:%02d", timezoneSeconds >= 0 ? "+" : "-", timezoneHours, timezoneMinutes);
            this.device.send("Timezone", timezone);
        } catch(Exception e) {
            LOG.warning(e.toString());
        }
    }

    @Override
    public void setPower(final Power p, Set<Port> source) throws MqttPersistenceException, MqttException {
        super.setPower(p, source);
        setStateCountTarget = setStateCount + 1;
        device.send(name, p.toString());
    }

    @Override
    public void setState(final String s){
        setStateCount++;
        if (setStateCount > setStateCountTarget) {
            try {
                super.setPower(Power.valueOf(s), null);
            } catch (Exception e) {}
        }
        super.setState(s);
    }

    public State getState() {
        return state;
    }
}
