package net.simplyrin.bungeefriends.tools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by SimplyRin on 2018/06/06.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class ThreadPool {

	private static final int THREADS = Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors()));
	private static ThreadPoolExecutor executorService = newExecutor();
	private static boolean stopped;

	private static ThreadPoolExecutor newExecutor() {
		return new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.AbortPolicy());
	}

	public static void run(Runnable runnable) {
		runAsync(runnable);
	}

	public static synchronized void runAsync(Runnable runnable) {
		if (stopped) throw new RejectedExecutionException("BungeeFriends is shutting down");
		executorService.execute(runnable);
	}

	public static synchronized void start() {
		if (executorService != null && executorService.isShutdown() && !executorService.isTerminated())
			throw new IllegalStateException("Previous BungeeFriends tasks are still running");
		if (executorService == null || executorService.isTerminated()) executorService = newExecutor();
		stopped = false;
	}

	public static boolean shutdown() {
		ThreadPoolExecutor closing;
		synchronized (ThreadPool.class) {
			stopped = true;
			closing = executorService;
			if (closing == null) return true;
			closing.shutdown();
		}
		try {
			if (closing.awaitTermination(30, TimeUnit.SECONDS)) {
				synchronized (ThreadPool.class) {
					if (executorService == closing) executorService = null;
				}
				return true;
			}
			closing.shutdownNow();
			closing.awaitTermination(5, TimeUnit.SECONDS);
			return false;
		} catch (InterruptedException e) {
			closing.shutdownNow();
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
