package home.common;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.google.firebase.database.DatabaseReference;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

public abstract class Port implements Comparable {
    public static final Logger LOG = Logger.getLogger(Port.class.getName());

    public State state = State.UNKNOWN;
    public DatabaseReference database = null;
    public String name = null;

    private Set<Port> listeners = new java.util.concurrent.ConcurrentSkipListSet<>();
    private Set<Port> sources = null;

    public void setPower(final String p) throws MqttPersistenceException, MqttException {
        setPower(Power.valueOf(p), null);
    }

    public void setPower(final Power p, Set<Port> sources) throws MqttPersistenceException, MqttException {
        // Update source list
        if (sources == null) {
            sources = new java.util.TreeSet<>();
        } else {
            sources = new java.util.TreeSet<>(sources);
        }
        if (sources.contains(this)){
            // This port was updated already
            return;
        }
        sources.add(this);

        LOG.finer("Set Port " + this.name + " power: " + p.toString());
        LOG.finest("Sources: " + sources);

        Iterator<Port> i = listeners.iterator();
        while (i.hasNext()){
            Port port = i.next();
            if (port.hasSource(this)){
                port.setPower(p, java.util.Collections.unmodifiableSet(sources));
            } else {
                i.remove();
            }
        }
    }

    public void setState(final String state) {
        LOG.finer("Set Port state: " + state);
        final State previousState = this.state;
        try {
            this.state = State.valueOf(state.toUpperCase());
        } catch (final Exception e) {
            LOG.warning("Could not update state");
            this.state = State.UNKNOWN;
        }
        if (previousState != this.state) {
            if (this.database != null) {
                this.database.child("state").setValueAsync(this.state);
            }
        }
    }

    public void addListener(Port p){
        listeners.add(p);
    }

    public boolean hasSource(Port p){
        if (this.sources == null){
            return false;
        }
        return this.sources.contains(p);
    }

    public void setSources(Set<Port> sources){
        this.sources = sources;
        sources.forEach(port -> port.addListener(this));
    }

    public int compareTo(Object o){
        return this.toString().compareTo(o.toString());
    }

    public boolean equals(Object o){
        return this == o;
    }
}