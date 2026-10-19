package net.simplyrin.bungeefriends.api.spigot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;
import net.simplyrin.bungeefriends.api.spigot.RequestLedger.Pending;

public class RequestLedgerTest {
	private static Pending request(UUID actor) {
		Message message = new Message(Protocol.REQUEST, UUID.randomUUID(), actor, "getOwner",
				Protocol.OK, Collections.emptyList());
		return new Pending(message, Protocol.encode(message), new CompletableFuture<>());
	}

	private static Message response(Pending request, UUID actor, byte status) {
		return new Message(Protocol.RESPONSE, request.message.id, actor, request.message.operation,
				status, Collections.singletonList(status == Protocol.OK ? "owner" : "denied"));
	}

	private static void expectFailure(Pending request, Class<? extends Throwable> cause) {
		try { request.result.join(); fail("Expected failed request"); }
		catch (CompletionException error) { assertTrue(cause.isInstance(error.getCause())); }
	}

	@Test
	public void responseMatchesRequestAndDuplicatesAreIgnored() {
		RequestLedger ledger = new RequestLedger();
		Pending request = request(UUID.randomUUID());
		assertTrue(ledger.register(request));
		assertFalse(ledger.receive(response(request, UUID.randomUUID(), Protocol.OK)));
		assertFalse(request.result.isDone());
		assertTrue(ledger.receive(response(request, request.actor, Protocol.OK)));
		assertEquals("owner", request.result.join().values.get(0));
		assertFalse(ledger.receive(response(request, request.actor, Protocol.OK)));
	}

	@Test
	public void rejectionTimeoutAndOfflineCompleteFutures() {
		RequestLedger ledger = new RequestLedger();
		UUID actor = UUID.randomUUID();
		Pending denied = request(actor);
		assertTrue(ledger.register(denied));
		assertTrue(ledger.receive(response(denied, actor, Protocol.DENIED)));
		expectFailure(denied, BungeeFriendsAPISpigot.ApiException.class);
		Pending timedOut = request(actor);
		assertTrue(ledger.register(timedOut));
		ledger.fail(timedOut, new TimeoutException("timed out"));
		expectFailure(timedOut, TimeoutException.class);
		Pending offline = request(actor);
		assertTrue(ledger.register(offline));
		ledger.failActor(actor, new IllegalStateException("offline"));
		expectFailure(offline, IllegalStateException.class);
	}

	@Test
	public void disableFailsOutstandingRequestsAndReenableAcceptsNewOnes() {
		RequestLedger ledger = new RequestLedger();
		Pending old = request(UUID.randomUUID());
		assertTrue(ledger.register(old));
		ledger.close(new IllegalStateException("disabled"));
		expectFailure(old, IllegalStateException.class);
		assertFalse(ledger.receive(response(old, old.actor, Protocol.OK)));
		assertFalse(ledger.register(request(UUID.randomUUID())));
		ledger.reopen();
		Pending current = request(UUID.randomUUID());
		assertTrue(ledger.register(current));
		assertTrue(ledger.receive(response(current, current.actor, Protocol.OK)));
	}
}
