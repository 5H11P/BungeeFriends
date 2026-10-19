package net.simplyrin.bungeefriends.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class ThreadPoolTest {
	@Test
	public void rejectsOverflowAndDrainsAcceptedTasks() throws Exception {
		ThreadPool.start();
		int workers = Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors()));
		CountDownLatch started = new CountDownLatch(workers);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger completed = new AtomicInteger();
		try {
			for (int i = 0; i < workers; i++) {
				ThreadPool.run(() -> {
					started.countDown();
					try {
						release.await();
						completed.incrementAndGet();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}
			assertTrue(started.await(5, TimeUnit.SECONDS));
			for (int i = 0; i < 1024; i++) ThreadPool.run(completed::incrementAndGet);
			try {
				ThreadPool.run(completed::incrementAndGet);
				fail("Expected queue rejection");
			} catch (RejectedExecutionException expected) {
				// The caller receives the rejection.
			}
		} finally {
			release.countDown();
			assertTrue(ThreadPool.shutdown());
		}
		assertEquals(workers + 1024, completed.get());
		try {
			ThreadPool.run(completed::incrementAndGet);
			fail("Expected submission after shutdown to fail");
		} catch (RejectedExecutionException expected) {
			// Shutdown must not recreate workers.
		}
		ThreadPool.start();
		ThreadPool.run(completed::incrementAndGet);
		assertTrue(ThreadPool.shutdown());
		assertEquals(workers + 1025, completed.get());
	}
}
