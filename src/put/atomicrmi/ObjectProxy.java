/*
 * Atomic RMI
 *
 * Copyright 2009-2010 Wojciech Mruczkiewicz <Wojciech.Mruczkiewicz@cs.put.poznan.pl>
 *                     Pawel T. Wojciechowski <Pawel.T.Wojciechowski@cs.put.poznan.pl>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details
 *
 * You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package put.atomicrmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

import put.atomicrmi.Access.Mode;

/**
 * An implementation of {@link IObjectProxy} interface. It is required to
 * control remote method invocations and implement versioning algorithm.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
class ObjectProxy extends UnicastRemoteObject implements IObjectProxy {

	/**
	 * Copy of object for the purpose of buffering writes and reading from
	 * buffer after release.
	 */
	private Object buffer = null;

	StateRecorder writeRecorder;

	/**
	 * A separate thread for performing buffering of an object that is used
	 * exclusively in read only mode. Up to one such thread can exist per object
	 * proxy.
	 * 
	 * @author Konrad Siek
	 */
	private class ReadThread extends Thread {

		private Semaphore semaphore = new Semaphore(0);
		private boolean commit;

		@Override
		public void run() {
			try {
				object.waitForCounter(px - 1); // 12
				object.transactionLock(tid);
				// we have to make a snapshot, else it thinks we didn't read the
				// object and in effect we don't get cv and rv
				snapshot = object.snapshot(); // 15
				buffer = object.clone(); // 13
				object.setCurrentVersion(px); // 14
				releaseTransaction(); // 16
			} catch (Exception e) {
				// FIXME the client-side should see the exceptions from this
				// thread.
				e.printStackTrace();
				throw new RuntimeException(e);
			} finally {
				object.transactionUnlock(tid);
			}

			semaphore.release(1); // 17 & 18

			// dismiss
			try {
				commit = waitForSnapshot(); // 19 & 20
				// line 21 will be taken care of in wait for snapshots
				finishTransaction(commit); // 22
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}

			// semaphore.release(RELEASED);
		}
	}

	private class WriteThread extends Thread {
		// StateRecorder writeRecorder;
		Object writeBuffer;

		@Override
		public void run() {
			try {
				// FIXME should this be synchronized within transaction to
				// prevent
				// some other operation concurrently doing stuff with
				// writeRecorder etc?
				object.waitForCounter(px - 1); // 24
				object.transactionLock(tid);

				// Short circuit, if pre-empted.
				if (writeRecorder == null) {
					object.transactionUnlock(tid);
					return;
				}

				// We have to make a snapshot, else it thinks we didn't read the
				// object and in effect we don't get cv and rv.
				snapshot = object.snapshot(); // 28

				writeRecorder.applyChanges(object); // 24-25

				writeRecorder = null; // prevent recorder from being used again
				writeBuffer = null; // release memory for buffer

				object.setCurrentVersion(px); // 27
				releaseTransaction(); // 29
			} catch (Exception e) {
				// FIXME the client-side should see the exceptions from this
				// thread.
				e.printStackTrace();
				throw new RuntimeException(e);
			} finally {
				object.transactionUnlock(tid);
			}
		}
	}

	/**
	 * Randomly generated serialization UID.
	 */
	private static final long serialVersionUID = -5524954471581314314L;

	/**
	 * The version counter value that determines when counter is released.
	 */
	private static final long RELEASED = -1;

	/**
	 * Remote reference to the transaction that this proxy is working for.
	 */
	private ITransaction transaction;

	/**
	 * Transaction unique identifier.
	 */
	private UUID tid;

	/**
	 * The wrapped remote object reference.
	 */
	private TransactionalUnicastRemoteObject object;

	/**
	 * Snapshot of the wrapped remote object. If <code>null</code> then snapshot
	 * is not present.
	 */
	private TransactionalUnicastRemoteObject.Snapshot snapshot;

	/**
	 * Determines if transaction is finished.
	 */
	private boolean over = false;

	/**
	 * The value of private counter for this remote object.
	 */
	private long px;

	/**
	 * The minor version counter that counts remote method invocations.
	 */
	private long mv;

	/**
	 * The minor write version counter that counts writes.
	 */
	private long mwv;
	
	/**
	 * The minor read version counter that counts writes.
	 */
	private long mrv;
	
	/**
	 * The minor any version counter that counts writes.
	 */
	private long mav;

	/**
	 * An upper bound on remote method invocations.
	 */
	private long ub;
	private long wub;
	private long rub;
	private long aub;

	/**
	 * Access mode to this remote object by this transaction: read-only,
	 * write-only, etc.
	 */
	private Mode mode;

	/**
	 * Thread that performs read-only optimization, created as needed.
	 */
	private ReadThread readThread;

	/**
	 * Thread that performs write-only asynchronous release, created as needed.
	 */
	private WriteThread writeThread;

	/**
	 * Creates the object proxy for given remote object.
	 * 
	 * @param transaction
	 *            transaction the proxy is created for.
	 * @param tid
	 *            transaction unique identifier.
	 * @param object
	 *            remote object that is being wrapped.
	 * @param calls
	 *            an upper bound on number of remote object invocations.
	 * @param writes
	 *            an upper bound on number of remote object writes. If unknown,
	 *            no of writes should equal no of calls (worst case).
	 * @param mode
	 *            access mode (read-only, write-only, etc.)
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	ObjectProxy(ITransaction transaction, UUID tid, TransactionalUnicastRemoteObject object, long calls, long writes,
			Mode mode) throws RemoteException {
		super();
		this.transaction = transaction;
		this.object = object;
		this.tid = tid;
		this.mode = mode;

		ub = calls;
		wub = writes;
		rub = calls;
		aub = calls;
		
		over = true;
	}

	/**
	 * Retrieves the transaction unique identifier that identifies transaction
	 * that this remote object proxy belongs to.
	 * 
	 * @return transaction unique identifier.
	 */
	UUID getTransactionId() {
		return tid;
	}

	public Object getWrapped(boolean useBuffer) throws RemoteException {
		if (useBuffer) {
			try {
				// this should have been handled by by preRead
				readThread.semaphore.acquire(1);
				readThread.semaphore.release(1);
				return buffer;
			} catch (Exception e) {
				e.printStackTrace();
				throw new RemoteException(e.getMessage(), e.getCause());
			}
		}
		return object;
	}

	public void startTransaction() throws RemoteException {
		TransactionFailureMonitor.getInstance().startMonitoring(this);
		px = object.startTransaction(tid);

		mv = 0;
		mwv = 0;
		mav = 0;
		mrv = 0;

		over = false;
	}

	/**
	 * Dear future me,
	 * 
	 * Watch out: lines 60--61 in the algorithm in the paper should probably
	 * say: if (Cw(xT) != dom(SwT) then start writerelease as thread THwrx join
	 * with THwrx.
	 * 
	 * While currently preWrite is just a copy of preAny. I have not started
	 * doing serious stuff here.
	 * 
	 * Best regards and sincere comiserations, Past me
	 */
	public boolean preWrite() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == RELEASED || mv == ub || mwv == wub) {
			throw new TransactionException("Upper bound is lower then number of invocations.");
		}

		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			snapshot = object.snapshot();
		} else {
			object.transactionLock(tid);
		}

		if (snapshot.getReadVersion() != object.getCurrentVersion()) {
			object.transactionUnlockForce(tid);
			transaction.rollback();
			throw new RollbackForcedException("Rollback forced during invocation.");
		}

		mv++;
		mwv++;

		return false;
	}

	public boolean preRead() throws RemoteException {
		if (mode == Mode.READ_ONLY) {
			// Read-only optimization (green).
			// We don't check for UB etc. because we already released this
			// object anyway, and we're using a buffer.
			try {
				// Synchronize with the Read Thread
				readThread.semaphore.acquire(1);
				readThread.semaphore.release(1);
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			// object.transactionLock(tid);

			mv++;
			mrv++;

			return true;
		} else if (writeRecorder != null) {
			// This occurs when the transaction was writing to a buffer, but
			// there is a
			// read. Since a read cannot be done from a write buffer, the
			// transaction must synchronize with the actual current state of the
			// actual shared object before proceeding.

			// Synchronize with existing state in order to proceed.
			object.waitForCounter(px - 1);
			object.transactionLock(tid);

			// Make snapshot.
			snapshot = object.snapshot();

			try {
				// Overwrite buffer to become consistent state.
				// buffer = object.clone();

				// Remove buffer completely, since
				buffer = null;

				// Roll back, if necessary.
				if (snapshot.getReadVersion() != object.getCurrentVersion()) {
					object.transactionUnlockForce(tid);
					transaction.rollback();
					// TODO nullify buffer, writeRecorder etc.?
					throw new RollbackForcedException("Rollback forced during read.");
				}

				// Use the write recorder to bring the consistent state up
				// to
				// date with the writes done prior to synch.
				writeRecorder.applyChanges(object);
				
				// It's an access.
				mv++;
				mrv++;

				return false;

			} catch (Exception e) {
				throw new RemoteException(e.getLocalizedMessage(), e.getCause());
			} finally {
				writeRecorder = null;
			}
		} else if (writeThread != null) { // 34
			// this occurs when the transaction writes to a buffer beforehand
			// and releases using a separate thread. The read must wait for
			// write release to complete before proceeding with the read. The
			// write thread will bring the buffer up to date, so the reads can
			// be done from the buffer without further synchronization.

			try {
				writeThread.join();
			} catch (InterruptedException e) {
				// Intentionally left empty.
			}
			
			// It's an access nevertheless.
			mv++;
			mrv++;

			return true;

		} else {
			// Read in a R/W object.
			return preAny();
		}

		// TODO (pink)
	}

	public boolean preAny() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == RELEASED || mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations.");
		}

		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			snapshot = object.snapshot();
		} else {
			object.transactionLock(tid);
		}

		if (snapshot.getReadVersion() != object.getCurrentVersion()) {
			object.transactionUnlockForce(tid);
			transaction.rollback();
			throw new RollbackForcedException("Rollback forced during invocation.");
		}

		mv++;
		mav++;

		return false;
	}

	public boolean preSync(Mode accessType) throws RemoteException {
		switch (accessType) {
		case READ_ONLY:
			return preRead();
		case WRITE_ONLY:
			return preWrite();
		case ANY:
		default:
			throw new RemoteException("Illegal access type: " + accessType);
		}
	}

	public void postRead() throws RemoteException {
		if (mode == Mode.READ_ONLY) {
			// if (mv == ub) {
			// object.setCurrentVersion(px);
			// releaseTransaction();
			// }
			// XXX this should be handled by read thread?

			// multiple reads should be able to coincide
			// object.transactionUnlock(tid);
		} else {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after release.");
			}

			if (mv == ub && object.checkCounter(px - 1)) { // checkCounter: 45
				object.setCurrentVersion(px);
				releaseTransaction();
			}

			object.transactionUnlock(tid);
		}
	}

	public void postAny() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == ub) {
			object.setCurrentVersion(px);
			releaseTransaction();
		}

		object.transactionUnlock(tid);
	}

	public void postSync(Mode accessType) throws RemoteException {
		switch (accessType) {
		case READ_ONLY:
			postRead();
			break;
		case WRITE_ONLY:
		case ANY:
		default:
			postAny();
		}
	}

	public void releaseTransaction() throws RemoteException {
		if (mv != RELEASED) {
			object.releaseTransaction();
			mv = RELEASED;
		}
	}

	public void finishTransaction(boolean restore) throws RemoteException {

		if (readThread != Thread.currentThread() && mode == Mode.READ_ONLY) { // 65
			// empty
		} else {
			TransactionFailureMonitor.getInstance().stopMonitoring(this);

			object.finishTransaction(tid, snapshot, restore);

			over = true;
			snapshot = null;
		}
	}

	public boolean waitForSnapshot() throws RemoteException {
		if (readThread != Thread.currentThread() && mode == Mode.READ_ONLY) { // line
																				// 57
			try {
				readThread.join(); // line 58
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			return readThread.commit; // line 21
		} else {
			object.waitForSnapshot(px - 1);

			if (mv != 0 && mv != RELEASED && snapshot.getReadVersion() == object.getCurrentVersion())
				object.setCurrentVersion(px);

			releaseTransaction();

			if (snapshot == null)
				return true;

			boolean commit = snapshot.getReadVersion() <= object.getCurrentVersion();
			return commit;
		}
	}

	/**
	 * Notification that transaction failure has been detected. This method is
	 * called by {@link TransactionFailureMonitor}. Object proxy should rollback
	 * wrapped object safely and disallow transaction commit.
	 * 
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	void OnFailure() throws RemoteException {
		System.out.println("Failure detected.");
		waitForSnapshot();
		finishTransaction(true);
	}

	/**
	 * Declare that the object will no longer be used by the current transaction
	 * and allow it to be used by other transactions.
	 * 
	 * @author K. Siek
	 * @throws RemoteException
	 */
	public void free() throws RemoteException {
		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			snapshot = object.snapshot();
		} else {
			object.transactionLock(tid);
		}

		object.setCurrentVersion(px);
		releaseTransaction();

		object.transactionUnlock(tid);

		over = true;
		// snapshot = null;
	}

	public void lock() throws RemoteException {
		object.transactionLock(tid);
	}

	public void unlock() throws RemoteException {
		object.transactionUnlock(tid);

		// Read-only optimization.
		// This has to be lumped in here with unlock, in order to minimize the
		// number of network messages sent between transaction and proxy.
		if (mode == Mode.READ_ONLY) {
			readThread = new ReadThread();
			readThread.start();
		}
	}

	public UUID getSortingKey() throws RemoteException {
		return object.getSortingKey();
	}

	public Mode getMode() throws RemoteException {
		return mode;
	}
}
