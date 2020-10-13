package io.temporal.samples.dataconverter;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.samples.model.Signup;
import io.temporal.samples.model.UserInfo;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converter App that executes a single activity. Requires a local instance the Temporal service to
 * be running.
 *
 * <p>This app uses a {@link CustomDataConverter} that overrides {@link
 * io.temporal.common.converter.DefaultDataConverter} and instead of default constructor of {@link
 * io.temporal.common.converter.JacksonJsonPayloadConverter} it overrides the {@link
 * com.fasterxml.jackson.databind.ObjectMapper} where encryption code using Jackson Crypto is
 * written.
 */
public class ConverterApp {

  private static final Logger log = LoggerFactory.getLogger(ConverterApp.class);
  static final String TASK_QUEUE = "ConverterQueue";

  @WorkflowInterface
  public interface SignupWorkflow {
    @WorkflowMethod
    Signup executeSignup(Signup signup);
  }

  @ActivityInterface
  public interface SignupActivities {
    @ActivityMethod
    Signup returnResponse(Signup request);
  }

  public static class SignupWorkflowImpl implements SignupWorkflow {

    private static final Logger log = LoggerFactory.getLogger(SignupWorkflowImpl.class);
    /**
     * Activity stub implements activity interface and proxies calls to it to Temporal activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final SignupActivities activities =
        Workflow.newActivityStub(
            SignupActivities.class,
            ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(2)).build());

    @Override
    public Signup executeSignup(Signup signupRequest) {

      log.info("Executing currently workflow request: {}", signupRequest);

      // This is a blocking call that returns only after the activity has completed.
      return activities.returnResponse(signupRequest);
    }
  }

  static class SignupActivitiesImpl implements SignupActivities {
    @Override
    public Signup returnResponse(Signup request) {
      return request;
    }
  }

  public static void main(String[] args) {

    WorkflowServiceStubs service = WorkflowServiceStubs.newInstance();

    // Configure workflow client to use a custom DataConverter.
    WorkflowClient client =
        WorkflowClient.newInstance(
            service,
            WorkflowClientOptions.newBuilder()
                .setDataConverter(CustomDataConverter.getDefaultInstance())
                .build());

    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(SignupWorkflowImpl.class);
    worker.registerActivitiesImplementations(new SignupActivitiesImpl());
    factory.start();

    SignupWorkflow workflow =
        client.newWorkflowStub(
            SignupWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

    Signup request =
        Signup.builder()
            .id("s1")
            .userInfo(
                UserInfo.builder()
                    .userName("user1")
                    .password("Wow!123")
                    .address("Chennai TN")
                    .build())
            .build();

    // Trigger workflow
    log.info("Submitted new workflow.");
    Signup response = workflow.executeSignup(request);

    log.info("Finally response received back is: {}", response);
    System.exit(0);
  }
}
