package put.atomicrmi.opt.test.search;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.ops.Read;
import put.atomicrmi.ops.Write;

public interface Account extends Remote {
	@Read
	public String getName() throws RemoteException;
	
	@Write
	public void deposit(int sum) throws RemoteException;

	@Write
	public void withdraw(int sum) throws RemoteException;

	@Read
	public int getBalance() throws RemoteException;
}
