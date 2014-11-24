package put.unit.writes.buffers;

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
 * 
 * Write sequence after read (after first write) test case. 
 * 
 * <pre>
 * T1 [ r(x)0       w(x)1  					           ]
 * T2  [      w(x)2        r(x)2 w(x)3  w(x)4  r(x)3  --!
 * </pre>
 * 
 * Checks whether a read will use the buffer and whether the state is
 * synchronized on commit. Commit of T2 is <b>NOT</b> blocked here until T1 commits.
 */
public class WriteSequenceAfterReadThenCommitOrder extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				int v1 = x.read();
				Assert.assertEquals(0, v1);
				
				waitForTick(3);

				waitForTick(4);
				x.write(1);

				waitForTick(9);

				t.commit();
				
				waitForTick(10);


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
				x.write(2);
				
				waitForTick(5);
				int v1 = x.read();
				Assert.assertEquals(2, v1);
				
				x.write(3);
				x.write(4);
				
				int v2 = x.read();
				Assert.assertEquals(4, v2);
				
				waitForTick(8);

				t.commit();
				
				waitForTick(9);

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
	public void writeFirstReadFromBufferthenAbortOrder() throws Throwable {
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(4, state("x"));
	}
}
