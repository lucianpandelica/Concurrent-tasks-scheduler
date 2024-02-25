/* Implement this class. */

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/* objects to be put in priority queue */
class MyTuple implements Comparable<MyTuple> {
    public Task task;
    public int timeStamp;

    public MyTuple(Task task, int timeStamp) {
        this.task = task;
        this.timeStamp = timeStamp;
    }

    /* keeps the right order in queue */
    @Override
    public int compareTo(MyTuple o) {
        if (this.task.getPriority() < o.task.getPriority()) {
            return 1;
        } else if (this.task.getPriority() > o.task.getPriority()) {
            return -1;
        } else {
            /*
                if two tasks have the same priority we consider
                time of arrival at host - higher prio for lower timestamp
             */
            if (this.timeStamp < o.timeStamp) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}
public class MyHost extends Host {
    public PriorityBlockingQueue<MyTuple> prioQ = new PriorityBlockingQueue<>();
    public AtomicLong workLeft = new AtomicLong(0);
    public AtomicInteger queueSize = new AtomicInteger(0);
    public AtomicInteger taskTimestamp = new AtomicInteger(0);
    public Object shutdownLatch = new Object();
    public Object updateLatch = new Object();
    public Object workLeftLatch = new Object();
    public long begTime = 0;
    public MyTuple crtTuple = null;
    public Task crtTask = null;
    public boolean crtFinished = false;
    public boolean currentlyRunning = false;

    @Override
    public void run() {

        while (true) {

            try {
                /* interrupt thread if it waits for nothing (empty queue) */
                synchronized (shutdownLatch) {
                    shutdownLatch.notify();
                }

                /* prepare execution of task */
                synchronized (updateLatch) {
                    /* take task with highest prio */
                    crtTuple = prioQ.take();
                    crtTask = crtTuple.task;
                    /* mark beginning of execution (for updateLatch) */
                    crtFinished = false;
                    /* store time of start */
                    begTime = System.currentTimeMillis();
                    /* mark beginning of execution (for workLeftLatch) */
                    synchronized (workLeftLatch) {
                        currentlyRunning = true;
                    }
                }

                /* execute task */
                Thread.sleep(crtTask.getLeft());

                /* task finished */
                synchronized (updateLatch) {
                    /* decrement 'workLeft' and mark end of execution for workLeftLatch */
                    synchronized (workLeftLatch) {
                        workLeft.addAndGet(-crtTask.getLeft());
                        currentlyRunning = false;
                    }
                    /* decrement queue size and mark end of exec for updateLatch */
                    queueSize.addAndGet(-1);
                    crtFinished = true;
                    crtTask.finish();
                }

            } catch (InterruptedException e) {
                /*
                   interrupt if signal was received from shutdown() function and
                   queue is empty (work done for this host)
                */
                if (queueSize.compareAndSet(0, 0)) {
                    break;
                }
                /* else, continue - execute another task */
            }
        }
    }

    @Override
    public void addTask(Task task) {
        try {
            /* add task to prio queue */
            int crtPrio = task.getPriority();
            int crtTimestamp = taskTimestamp.incrementAndGet();
            prioQ.add(new MyTuple(task, crtTimestamp));

            /* update host parameters */
            queueSize.addAndGet(1);
            workLeft.addAndGet(task.getDuration());

            /* check if currently running task must be preempted by the new task */
            synchronized (updateLatch) {
                int prio = crtTask.getPriority();
                boolean isPreemptible = crtTask.isPreemptible();

                /*
                    if a task is running, is preeptable and
                    has lower prio than the new task
                */
                if (crtFinished == false && isPreemptible && crtPrio > prio) {

                    /* interrupt current task */
                    if (prio != -1) {
                        /* calculate how many seconds it ran */
                        long crtTime = System.currentTimeMillis();
                        MyTuple oldTuple = crtTuple;
                        long oldBegTime = begTime;
                        long oldLeft;
                        long workDone;

                        /* start next task */
                        this.interrupt();

                        /* update 'workLeft' and mark end of exec for workLeftLatch */
                        synchronized (workLeftLatch) {
                            currentlyRunning = false;
                            oldLeft = oldTuple.task.getLeft();
                            double diff_sec = ((double) (crtTime - oldBegTime)) / 1000.0;
                            workDone = Math.min(Math.round(diff_sec) * 1000L, oldLeft);
                            workLeft.addAndGet(- workDone);
                        }

                        /* update task duration */
                        oldTuple.task.setLeft(oldLeft - workDone);

                        /* check left duration */
                        if (oldTuple.task.getLeft() > 0L) {
                            /* add preempted task to queue */
                            prioQ.add(oldTuple);
                        } else {
                            /* task is finished - decrement queueSize */
                            oldTuple.task.finish();
                            queueSize.addAndGet(-1);
                        }
                    }
                }
            }
        }
        catch (ClassCastException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getQueueSize() {
        return queueSize.get();
    }

    @Override
    public long getWorkLeft() {
        long workDone = 0L;

        /*
            if a task is currently running, approximate how many seconds it ran
            and substract that amount from value of 'workLeft';
            else, just return 'workLeft'.
         */
        synchronized (workLeftLatch) {
            if (currentlyRunning == true) {
                long crtTime = System.currentTimeMillis();
                double diff_sec = ((double) (crtTime - begTime)) / 1000.0;
                workDone = Math.min(Math.round(diff_sec) * 1000L, crtTask.getLeft());
            }

            return workLeft.get() - workDone;
        }
    }

    @Override
    public void shutdown() {

        /*
            after shutdown() is called from Main,
            wait for host to finish the work and interrupt its execution
        */
        synchronized (shutdownLatch) {
            while (!queueSize.compareAndSet(0, 0)) {
                try {
                    shutdownLatch.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        this.interrupt();
    }
}