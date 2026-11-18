package services.applicationapi.tripplan.service.savedplan;

public interface GroupPlanNotificationPublisher {

    void publish(GroupPlanSavedEvent event);
}
