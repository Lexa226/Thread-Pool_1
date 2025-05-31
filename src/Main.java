
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CustomExecutor executor = new CustomThreadPool(
                2,    // corePoolSize
                4,    // maxPoolSize
                5,    // keepAliveTime
                TimeUnit.SECONDS,
                5,    // queueSize
                1     // minSpareThreads
        );

        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            executor.execute(() -> {
                System.out.println("[Task] Started Task " + taskId + " on " + Thread.currentThread().getName());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("[Task] Finished Task " + taskId + " on " + Thread.currentThread().getName());
            });
        }

        Thread.sleep(15000); // Даем время задачам выполниться
        System.out.println("[Main] Initiating shutdown...");
        executor.shutdown();

        Thread.sleep(7000); // Ожидаем завершения всех задач
        System.out.println("[Main] Finished.");
    }
}
