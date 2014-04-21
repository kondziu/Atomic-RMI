package soa.atomicrmi.test.basic;

import java.rmi.Remote;
import java.rmi.RemoteException;

import soa.atomicrmi.TransactionalUnicastRemoteObject;

interface A extends Remote {
	public String getName() throws RemoteException;

	public void setName(String name) throws RemoteException;

	public B getB() throws RemoteException;

	public B2 getB2() throws RemoteException;
}

class ImplA extends TransactionalUnicastRemoteObject implements A {
	private static final long serialVersionUID = 1L;

	private String name;

	protected ImplA() throws RemoteException {
		super();
	}

	public B getB() throws RemoteException {
		return new ImplB();
	}

	public B2 getB2() throws RemoteException {
		return new B2();
	}

	public String getName() throws RemoteException {
		return name;
	}

	public void setName(String name) throws RemoteException {
		this.name = name;
	}

}