package home.device;

public class IOPort {
    private final Device device;
    private final String name;

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

    protected IOPort(final Device device, final String name){
        this.device = device;
        this.name = name;
    }

    public void setPower(Power p) throws Exception{
        device.send(name, p.toString());
    }

    public void updateState(String state){
        try{
            this.state = State.valueOf(state.toUpperCase());
        } catch(Exception e) {
            this.state = State.UNKNOWN;
        }
    }
}
