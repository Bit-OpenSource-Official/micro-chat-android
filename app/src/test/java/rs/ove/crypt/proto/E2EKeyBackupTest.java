package rs.ove.crypt.proto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class E2EKeyBackupTest {
	@Test
	public void restoresSameIdentityOnAnotherDevice() throws Exception {
		E2ECipher.Identity firstDevice = E2ECipher.generateIdentity();
		E2EKeyBackup.Backup backup = E2EKeyBackup.seal(firstDevice, "account-password");
		E2ECipher.Identity secondDevice = E2EKeyBackup.open(backup, "account-password");
		assertEquals(firstDevice.privateKeyB64, secondDevice.privateKeyB64);
		assertEquals(firstDevice.publicKeyB64, secondDevice.publicKeyB64);
	}

	@Test(expected = java.io.IOException.class)
	public void wrongPasswordIsRejected() throws Exception {
		E2ECipher.Identity identity = E2ECipher.generateIdentity();
		E2EKeyBackup.Backup backup = E2EKeyBackup.seal(identity, "correct-password");
		E2EKeyBackup.open(backup, "wrong-password");
	}
}
