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

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

import put.atomicrmi.Transaction.AccessType;

/**
 * Internal transactional mechanism of {@link TransactionalUnicastRemoteObject}
 * class. Specifies methods required to initiate remote transaction.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface ITransactionalRemoteObject extends Remote {

	/**
	 * Creates and gives a remote object proxy. Object proxy wraps an instance
	 * of particular remote object and provides mechanism to monitor
	 * invocations.
	 * 
	 * @param transaction
	 *            a transaction remote object.
	 * @param tid
	 *            transaction unique identifier.
	 * @param calls
	 *            upper bound on number of remote object invocations.
	 * @return an instance of object proxy that wraps this remote object.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	IObjectProxy createProxy(ITransaction transaction, UUID tid, long calls, AccessType type) throws RemoteException;

	/**
	 * Gives a transaction failure monitor used at specific node where this
	 * remote object is placed.
	 * 
	 * @return a remote handle to transaction failure detector.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	ITransactionFailureMonitor getFailureMonitor() throws RemoteException;
}