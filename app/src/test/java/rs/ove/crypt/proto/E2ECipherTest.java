package rs.ove.crypt.proto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class E2ECipherTest {
	@Test
	public void peersCanEncryptAndAuthenticateMessages() throws Exception {
		E2ECipher.Identity alice = E2ECipher.generateIdentity();
		E2ECipher.Identity bob = E2ECipher.generateIdentity();
		E2ECipher.Envelope envelope = E2ECipher.seal(
				alice, bob.publicKeyB64, "alice", "bob", "private text"
		);
		assertEquals(
				"private text",
				E2ECipher.open(bob, alice.publicKeyB64, "alice", "bob", envelope)
		);
	}

	@Test
	public void versionTwoUsesStableUserIdsWithoutUsernames() throws Exception {
		E2ECipher.Identity alice = E2ECipher.generateIdentity();
		E2ECipher.Identity bob = E2ECipher.generateIdentity();
		E2ECipher.Session aliceSession = E2ECipher.session(
				alice, bob.publicKeyB64, "4a10c001", "4a10c002"
		);
		E2ECipher.Envelope envelope = E2ECipher.sealV2(
				aliceSession, "4a10c001", "4a10c002", "hello without usernames"
		);
		E2ECipher.Session bobSession = E2ECipher.session(
				bob, alice.publicKeyB64, "4a10c001", "4a10c002"
		);

		assertEquals(2, envelope.version);
		assertEquals(
				"hello without usernames",
				E2ECipher.open(bobSession, "4a10c001", "4a10c002", envelope)
		);
	}

	@Test(expected = java.io.IOException.class)
	public void changedTagIsRejected() throws Exception {
		E2ECipher.Identity alice = E2ECipher.generateIdentity();
		E2ECipher.Identity bob = E2ECipher.generateIdentity();
		E2ECipher.Envelope envelope = E2ECipher.seal(
				alice, bob.publicKeyB64, "alice", "bob", "private text"
		);
		byte[] tag = Base64Codec.decode(envelope.tag);
		tag[0] ^= 1;
		E2ECipher.open(
				bob, alice.publicKeyB64, "alice", "bob",
				new E2ECipher.Envelope(
						envelope.version, envelope.nonce,
						envelope.ciphertext, Base64Codec.encode(tag)
				)
		);
	}
}
