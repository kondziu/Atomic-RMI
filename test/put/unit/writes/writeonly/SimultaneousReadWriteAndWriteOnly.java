package put.unit.writes.writeonly;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.OneThreadToRuleThemAll;
import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionFailureMonitor;
import put.atomicrmi.Update;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * R/W transaction simultaneous with a write-only transaction.
 * 
 * 
 * <pre>
 *     [  w(x)2 w(x)3 w(y)1 ]
 *      [ r(x)0 w(x)1 ]
 * </pre>
 * 
 */
public class SimultaneousReadWriteAndWriteOnly extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Update();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				x.write(2);

				x.write(3);

				y.write(1);

				waitForTick(4);
				t.commit();

				waitForTick(7);
				waitForTick(8);

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

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				x.write(v1 + 1);

				waitForTick(3);
				t.commit();
				waitForTick(4);

				waitForTick(8);

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
	public void simultaneousReadWriteAndWriteOnly() throws Throwable {
		OneThreadToRuleThemAll.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(3, state("x"));
		Assert.assertEquals(1, state("y"));
	}
}
