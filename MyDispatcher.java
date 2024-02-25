/* Implement this class. */

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

public class MyDispatcher extends Dispatcher {
    /*
       keep track of last host ID for Round Robin
       because multiple threads can call addTask method
    */
    public AtomicInteger lastHostID = new AtomicInteger(-1);

    public MyDispatcher(SchedulingAlgorithm algorithm, List<Host> hosts) {
        super(algorithm, hosts);
    }

    @Override
    public void addTask(Task task) {
        if (this.algorithm == SchedulingAlgorithm.ROUND_ROBIN) {
            /* find host for task */
            if (lastHostID.compareAndSet(-1, 0)) {
                /* first task to be added - send to first host */
                hosts.get(0).addTask(task);
            } else {
                /* computes host ID */
                IntUnaryOperator op = val -> {
                    int res = (val + 1) % hosts.size();
                    return res;
                };

                /* get new host ID and update last host ID */
                int newHostID = lastHostID.updateAndGet(op);

                /* add task to host */
                hosts.get(newHostID).addTask(task);
            }
        }

        if (this.algorithm == SchedulingAlgorithm.SHORTEST_QUEUE) {
            /*
                we can use the dispatcher as sync obj because each test runs
                only one scheduling algorithm
            */
            synchronized (this) {
                int minSize = hosts.get(0).getQueueSize();
                int hostID = 0;

                /* find minimum queue size */
                for (int i = 1; i < hosts.size(); i++) {
                    int crtSize = hosts.get(i).getQueueSize();
                    if (crtSize < minSize) {
                        minSize = crtSize;
                        hostID = i;
                    }
                }

                /* add task to host */
                hosts.get(hostID).addTask(task);
            }
        }

        if (this.algorithm == SchedulingAlgorithm.SIZE_INTERVAL_TASK_ASSIGNMENT) {
            /*
                we don't need a sync block because we use
                BlockingQueue in MyHost class
            */
            switch(task.getType()) {
                case SHORT:
                    hosts.get(0).addTask(task);
                    break;
                case MEDIUM:
                    hosts.get(1).addTask(task);
                    break;
                case LONG:
                    hosts.get(2).addTask(task);
                    break;
                default:
                    break;
            }
        }

        if (this.algorithm == SchedulingAlgorithm.LEAST_WORK_LEFT) {
            /*
                used a sync block to make sure the 'workLeft' variable for
                each host doesn't increase during this search
            */
            synchronized (this) {
                long minWorkLeft = hosts.get(0).getWorkLeft();
                int hostID = 0;

                /* find LWL */
                for (int i = 1; i < hosts.size(); i++) {
                    long crtWorkLeft = hosts.get(i).getWorkLeft();
                    if (crtWorkLeft < minWorkLeft) {
                        minWorkLeft = crtWorkLeft;
                        hostID = i;
                    }
                }

                /* add task to host */
                hosts.get(hostID).addTask(task);
            }
        }
    }
}
