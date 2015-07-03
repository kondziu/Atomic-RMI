package put.unit.generic;

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
 * Early release test case.
 * 
 * <pre>
 * T1 [ r(x)0 w(x)1 r(y)0 w(y)1 ]
 * T2  [            r(x)1 w(x)2 ]
 * </pre>
 * 
 * Specifically checks whether T2 is free to operate on X while T1 operates on
 * y.
 */
public class EarlyRelease extends RMITest {
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
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				int vy = y.read();
				y.write(vy + 1);

				waitForTick(3);

				// If early release worked correctly, T2 should be free to
				// set aint to 1 before tick 3.
				Assert.assertEquals(1, aint.get());

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
				OneThreadToRuleThemAll.theOneThread.emergencyStop();
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

				int vx = x.read();
				x.write(vx + 1);

				aint.set(1);

				waitForTick(3);

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
				OneThreadToRuleThemAll.theOneThread.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}
	}

	@Test
	public void earlyRelease() throws Throwable {
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(2, state("x"));
		Assert.assertEquals(1, state("y"));
	}
}
