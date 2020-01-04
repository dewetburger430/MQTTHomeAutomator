package home.device;

import com.google.firebase.database.DatabaseReference;

public class IOPort {
    private final Device device;
    private final String name;
    private final DatabaseReference database;

    private State state = State.UNKNOWN;

    public enum State{
        UNKNOWN,
        ON,
        OFF,
    }

    public enum Power{
        ON,
        OFF,
        TOGGLE,
    }

    protected IOPort(final Device device, final String name, final DatabaseReference database){
        this.device = device;
        this.name = name;
        this.database = database.child(name.toUpperCase());
        this.database.setValueAsync(this);
    }

    public void setPower(final Power p) throws Exception {
        device.send(name, p.toString());
    }

    public void setState(final String state) {
        try {
            final State ps = this.state;
            this.state = State.valueOf(state.toUpperCase());
            if (ps != this.state) {
                this.database.setValueAsync(this);
            }
        } catch (final Exception e) {
            this.state = State.UNKNOWN;
        }
    }

    public State getState() {
        return state;
    }
}
