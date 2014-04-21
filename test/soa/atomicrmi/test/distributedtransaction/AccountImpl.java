package soa.atomicrmi.test.distributedtransaction;

import java.rmi.RemoteException;

import soa.atomicrmi.TransactionalUnicastRemoteObject;

public class AccountImpl extends TransactionalUnicastRemoteObject implements Account {

	private static final long serialVersionUID = 5553833002385560844L;

	int balance;

	protected AccountImpl(int balance) throws RemoteException {
		super();
		this.balance = balance;
	}

	public void deposit(int sum) throws RemoteException {
		balance += sum;
	}

	public void withdraw(int sum) throws RemoteException {
		balance -= sum;
	}

	public int getBalance() throws RemoteException {
		return balance;
	}
}
