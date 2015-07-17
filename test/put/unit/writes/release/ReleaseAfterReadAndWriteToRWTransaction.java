package put.unit.writes.release;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.OneThreadToRuleThemAll;
import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionFailureMonitor;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Release variable after a read and a write.
 * 
 * <pre>
 * T1 [ r(x)0 w(x)1       r(x)1      ] 
 * T2  [            r(x)1 w(x)2 r(x)2 ]
 * </pre>
 */
public class ReleaseAfterReadAndWriteToRWTransaction extends RMITest {
	class Threads extends MultithreadedTest {

		AtomicInteger aint;

		@Override
		public void initialize() {
			aint = new AtomicInteger(0);
		}

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 3, 2, 1);

				t.start();
				waitForTick(1);
				waitForTick(2);

				int v0 = x.read();
				Assert.assertEquals(0, v0);

				x.write(1);
				aint.set(1);

				waitForTick(3);
				waitForTick(4);

				int v = x.read();
				Assert.assertEquals(1, v);
				aint.set(2);

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
				OneThreadToRuleThemAll.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);

				waitForTick(3);

				Assert.assertEquals("T1 should release after write not after read.", 1, aint.get());
				int v1 = x.read();
				Assert.assertEquals(1, v1);

				waitForTick(4);

				x.write(2);

				int v2 = x.read();
				Assert.assertEquals(2, v2);

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
				OneThreadToRuleThemAll.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}
	}

	@Test
	public void releaseAfterReadAndWriteToRWTransaction() throws Throwable {
		OneThreadToRuleThemAll.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(2, state("x"));
	}
}
