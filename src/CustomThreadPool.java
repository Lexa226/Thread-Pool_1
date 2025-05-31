import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

class CustomThreadPool implements CustomExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int minSpareThreads;
    private final BlockingQueue<Runnable> taskQueue;
    private final Set<Worker> workers = ConcurrentHashMap.newKeySet();
    private final Map<Worker, Thread> workerThreads = new ConcurrentHashMap<>();
    private final ThreadFactory threadFactory;
    private final RejectedExecutionHandler rejectedHandler;
    private volatile boolean isShutdown = false;

    private final AtomicInteger threadCount = new AtomicInteger(0);

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
                            int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.minSpareThreads = minSpareThreads;
        this.taskQueue = new ArrayBlockingQueue<>(queueSize);
        this.threadFactory = new CustomThreadFactory("MyPool");
        this.rejectedHandler = new DefaultRejectedHandler();
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            log("[Rejected] Task rejected. Pool is shutting down.");
            return;
        }

        if (!taskQueue.offer(command)) {
            if (threadCount.get() < maxPoolSize) {
                addWorker(command);
            } else {
                rejectedHandler.rejectedExecution(command, null);
            }
        } else {
            log("[Pool] Task accepted into queue: " + command);
            ensureMinSpareThreads();
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(task);
        execute(future);
        return future;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        for (Worker w : workers) {
            w.interruptIfIdle();
        }
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        for (Worker w : workers) {
            Thread t = workerThreads.get(w);
            if (t != null) t.interrupt();
        }
        taskQueue.clear();
    }

    private void addWorker(Runnable firstTask) {
        Worker worker = new Worker(firstTask);
        workers.add(worker);
        Thread thread = threadFactory.newThread(worker);
        workerThreads.put(worker, thread);
        threadCount.incrementAndGet();
        thread.start();
    }

    private void ensureMinSpareThreads() {
        long idleCount = workers.stream().filter(Worker::isIdle).count();
        if (idleCount < minSpareThreads && threadCount.get() < maxPoolSize) {
            addWorker(null);
        }
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private class Worker implements Runnable {
        private Runnable firstTask;
        private volatile boolean idle = true;

        Worker(Runnable firstTask) {
            this.firstTask = firstTask;
        }

        public boolean isIdle() {
            return idle;
        }

        public void interruptIfIdle() {
            Thread t = workerThreads.get(this);
            if (idle && t != null) t.interrupt();
        }

        @Override
        public void run() {
            try {
                Runnable task = this.firstTask;
                this.firstTask = null;
                while (!isShutdown || !taskQueue.isEmpty()) {
                    if (task == null) {
                        task = taskQueue.poll(keepAliveTime, timeUnit);
                    }
                    if (task != null) {
                        idle = false;
                        log("[Worker] " + Thread.currentThread().getName() + " executes " + task);
                        try {
                            task.run();
                        } catch (Exception e) {
                            log("[Error] Exception in task: " + e.getMessage());
                        }
                        idle = true;
                        task = null;
                    } else if (threadCount.get() > corePoolSize) {
                        log("[Worker] " + Thread.currentThread().getName() + " idle timeout, stopping.");
                        break;
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                workers.remove(this);
                workerThreads.remove(this);
                int remaining = threadCount.decrementAndGet();
                log("[Worker] " + Thread.currentThread().getName() + " terminated. Remaining: " + remaining);
            }
        }
    }

    private static class CustomThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger count = new AtomicInteger(1);

        CustomThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            String name = baseName + "-worker-" + count.getAndIncrement();
            log("[ThreadFactory] Creating new thread: " + name);
            return new Thread(r, name);
        }

        private void log(String msg) {
            System.out.println(msg);
        }
    }

    private static class DefaultRejectedHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            System.out.println("[Rejected] Task " + r + " was rejected due to overload!");
        }
    }
}
