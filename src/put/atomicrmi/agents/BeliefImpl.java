package put.atomicrmi.agents;

import put.atomicrmi.optsva.objects.TransactionalUnicastRemoteObject;

import java.io.Closeable;
import java.io.Serializable;
import java.rmi.RemoteException;

/**
 * Created by ksiek on 24.01.17.
 */
public class BeliefImpl<T> extends TransactionalUnicastRemoteObject implements Belief<T>, Serializable, Cloneable {
    private T value;
    private boolean predicate;

    protected BeliefImpl(T value, boolean initiallyTrue) throws RemoteException {
        this.value = value;
        this.predicate = initiallyTrue;
    }

    @Override
    public T read() {
        return value;
    }

    @Override
    public void write(T value) {
        this.value = value;
    }

    @Override
    public boolean isTrue() {
        return predicate;
    }

    @Override
    public void setTrue() {
        predicate = true;
    }

    @Override
    public void setFalse() {
        predicate = false;
    }
}
