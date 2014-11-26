package put.unit.writes.release;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionFailureMonitor;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Release variable after a sequence of writes.
 * 
 * <pre>
 * T1 [ w(x)1 w(x)3       r(x)3 ] 
 * T2  [            w(x)2 r(x)2  ]
 * </pre>
 */
public class ReleaseAfterMoreBufferedWritesToWRTransaction extends RMITest {
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
				Variable x = t.accesses((Variable) registry.lookup("x"), 3, 1, 2);

				t.start();
				waitForTick(1);
				waitForTick(2);

				x.write(1);
				x.write(3);
				aint.set(1);

				waitForTick(3);
				waitForTick(4);

				int v = x.read();
				Assert.assertEquals(3, v);
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
				x.write(2);

				waitForTick(4);

				int v = x.read();
				Assert.assertEquals(2, v);
				
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
	public void releaseAfterOneBufferedWriteToWRTransaction() throws Throwable {
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(2, state("x"));
	}
}
