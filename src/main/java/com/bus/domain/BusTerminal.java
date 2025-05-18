package com.bus.domain;

public class BusTerminal extends BusStop {
    // Terminals might have specific properties in the future,
    // for now, it's mainly a type distinction.

    public BusTerminal() {
        super();
    }

    public BusTerminal(String id, String name) {
        super(id, name);
    }

    @Override
    public String toString() {
        return "BusTerminal{" +
               "id='" + getId() + '\'' +
               ", name='" + getName() + '\'' +
               '}';
    }
    // Equals and hashCode are inherited from BusStop and should work fine
    // if based on 'id'. If we add terminal-specific fields that affect equality,
    // we'd need to override them here.
}