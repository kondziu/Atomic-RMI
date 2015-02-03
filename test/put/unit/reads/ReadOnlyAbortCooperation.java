package put.unit.reads;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionFailureMonitor;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Read-only abort.
 * 
 * <pre>
 * T1 [ r(x)0                    ]
 * T2  [      r(x)0               ]
 * </pre>
 * 
 * Checks whether a read-only transaction reacts correctly to sharing variables.
 */
public class ReadOnlyAbortCooperation extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.reads((Variable) registry.lookup("x"), 1);

				t.start();
				waitForTick(1);
				waitForTick(2);

				int v = x.read();
				Assert.assertEquals(0, v);

				waitForTick(3);

				waitForTick(4);

				t.commit();

				waitForTick(5);

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.reads((Variable) registry.lookup("x"), 1);

				waitForTick(1);
				t.start();
				waitForTick(2);

				waitForTick(3);

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(4);
				waitForTick(5);

				t.commit();

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}
	}

	@Test
	public void readOnlyAbort() throws Throwable {
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
	}
}