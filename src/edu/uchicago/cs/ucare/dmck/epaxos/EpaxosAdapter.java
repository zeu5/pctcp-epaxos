package edu.uchicago.cs.ucare.dmck.epaxos;

class EpaxosAdapter {

    // Interface to talk to Epaxos master

    private static EpaxosAdapter instance = null;

    private EpaxosAdapter() {}

    public EpaxosAdapter getInstance() {
        if(instance == null) {
            instance = new EpaxosAdapter();
        }
        return instance;
    }
}