package services.routingservice.routecalculation.exception;

public class NoSafeRouteException extends RuntimeException
{
        public enum Reason {
            NO_PATH,
            TOO_MANY_ZONES,
            POINT_IN_DANGER_ZONE
        }

    private final Reason reason;

    public NoSafeRouteException(Reason reason, String message)
    {
        super(message);
        this.reason = reason;
    }

    public Reason getReason()
    {
        return reason;
    }
}
