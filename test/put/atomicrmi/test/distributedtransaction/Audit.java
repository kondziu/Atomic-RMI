package put.atomicrmi.test.distributedtransaction;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.Transaction;
import put.atomicrmi.test.tools.User;

public class Audit {
	public static void main(String[] args) throws RemoteException, NotBoundException {
		// Parse the commandline arguments to figure out the hostname and port
		// of RMI registry.
		if (args.length < 2) {
			System.err.println("Required arguments: " + "hostname and port of RMI registry.");
			System.exit(1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.getRegistry(host, port);

		// Get references to remote objects.
		Account a = (Account) registry.lookup("A");
		Account b = (Account) registry.lookup("B");
		Account c = (Account) registry.lookup("C");
		Account d = (Account) registry.lookup("D");

		// Wait for the user.
		System.out.println("About to run transactional code.");
		User.waitUp();

		// Transaction header.
		Transaction transaction = new Transaction();
		Account ta = (Account) transaction.accesses(a, 1);
		Account tb = (Account) transaction.accesses(b, 1);
		Account tc = (Account) transaction.accesses(c, 1);
		Account td = (Account) transaction.accesses(d, 1);

		transaction.start();

		// Check balance on both accounts.
		System.out.println("Balance on A: " + ta.getBalance());
		System.out.println("Balance on B: " + tb.getBalance());
		System.out.println("Balance on C: " + tc.getBalance());
		System.out.println("Balance on D: " + td.getBalance());

		// Wait to check pre- and post- commit behavior.
		System.out.println("Waiting before commit.");
		User.waitUp();

		transaction.commit();

		System.out.println("All is well.");

		System.exit(0);
	}
}