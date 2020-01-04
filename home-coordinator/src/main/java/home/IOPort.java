package home;

public class IOPort {
    private final Device device;
    private final String name;

    private State state = State.UNKNOWN;

    enum State{
        UNKNOWN,
        ON,
        OFF,
    }

    enum Power{
        ON,
        OFF,
        TOGGLE,
    }

    public IOPort(final Device device, final String name){
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
