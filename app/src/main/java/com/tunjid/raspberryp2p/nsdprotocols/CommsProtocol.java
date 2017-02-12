package com.tunjid.raspberryp2p.nsdprotocols;

/**
 * Interface for Server communication with input from client
 * <p>
 * Created by tj.dahunsi on 2/6/17.
 */

public interface CommsProtocol {

    String processInput(String input);

}