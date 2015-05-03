import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class Scheduler {

    private final int threadCount;
    private final WorkerThread[] threads;
    private final List<TaskFuture<Long>> futures = new ArrayList<>();
    private final Map<Long, TaskFuture<Long>> allFutures = new HashMap<>();

    public Scheduler(int threadCount) {
        this.threadCount = threadCount;
        threads = new WorkerThread[threadCount];

        for (WorkerThread thread : threads) {
            thread = new WorkerThread();
            thread.start();
        }
    }

    public TaskFuture<Long> submit(Callable task) {
        TaskFuture<Long> taskFuture = new TaskFuture<>();
        taskFuture.setTask(task);
        taskFuture.setStatus(TaskFuture.Status.WAITING);
        allFutures.put(taskFuture.getId(), taskFuture);
        synchronized (futures) {
            futures.add(taskFuture);
            futures.notify();
        }
        return taskFuture;
    }

    public Optional<TaskFuture<Long>> getFutureById(long id) {
        return Optional.ofNullable(allFutures.get(id));
    }

    public Optional<TaskFuture.Status> getStatus(long id) {
        Optional<TaskFuture<Long>> future = getFutureById(id);
        if (future.isPresent()) {
            return Optional.of(future.get().getStatus());
        } else {
            return Optional.empty();
        }
    }

    public boolean interrupt(long id) {
        Optional<TaskFuture<Long>> mayBeFuture = getFutureById(id);
        if (mayBeFuture.isPresent()) {
//            TODO: COMPLETED?
            TaskFuture<Long> future = mayBeFuture.get();
            future.interrupt();
            return true;
        } else {
            return false;
        }
    }

    public class WorkerThread extends Thread {
        @Override
        public void run() {
            while (true) {
                TaskFuture<Long> future;
                synchronized (futures) {
                    while (futures.isEmpty()) {
                        try {
                            futures.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    future = futures.remove(0);
                }
                future.setThread(this);
                future.setStatus(TaskFuture.Status.RUNNING);
                try {
                    try {
                        future.getTask().call();
                        future.setStatus(TaskFuture.Status.COMPLETED);
                    } catch (Exception e) {
                        future.setStatus(TaskFuture.Status.INTERRUPTED);
                    }
                } catch (RuntimeException e) {
                    // If we don't catch RuntimeException,
                    // the pool could leak threads
                }
            }
        }
    }
}
