package com.tunjid.raspberrynetworkservicediscovery.services;

import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.tunjid.raspberrynetworkservicediscovery.NsdHelper;
import com.tunjid.raspberrynetworkservicediscovery.abstractclasses.BaseService;
import com.tunjid.raspberrynetworkservicediscovery.abstractclasses.RegistrationListener;
import com.tunjid.raspberrynetworkservicediscovery.nsdprotocols.CommsProtocol;
import com.tunjid.raspberrynetworkservicediscovery.nsdprotocols.ProxyProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Service hosting a {@link CommsProtocol} on network service discovery
 */
public class ServerService extends BaseService {

    private static final String TAG = ServerService.class.getSimpleName();

    private String serviceName;
    private ServerThread serverThread;

    private final IBinder binder = new ServerServiceBinder();
    private final RegistrationListener registrationListener = new RegistrationListener() {

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            super.onServiceRegistered(serviceInfo);
            ServerService.this.serviceName = serviceInfo.getServiceName();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        nsdHelper.initializeRegistrationListener(registrationListener);
        serverThread = new ServerThread(nsdHelper);
        serverThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public String getServiceName() {
        return serviceName;
    }

    protected void tearDown() {
        super.tearDown();
        serverThread.tearDown();
    }

    /**
     * {@link Binder} for {@link ServerService}
     */
    public class ServerServiceBinder extends Binder {
        public ServerService getServerService() {
            return ServerService.this;
        }
    }

    /**
     * Thread for communications between {@link ServerService} and it's clients
     */
    private static class ServerThread extends Thread {

        volatile boolean isRunning;

        private ServerSocket serverSocket;

        ServerThread(NsdHelper helper) {

            // Since discovery will happen via Nsd, we don't need to care which port is
            // used, just grab an isAvailable one and advertise it via Nsd.
            try {
                serverSocket = new ServerSocket(0);
                helper.registerService(serverSocket.getLocalPort());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            isRunning = true;

            while (isRunning) {
                try {
                    Log.d(TAG, "ServerSocket Created, awaiting connection.");
                    // Create new clients for every connection received
                    new Connection(serverSocket.accept());
                }
                catch (Exception e) {
                    Log.e(TAG, "Error creating ServerSocket: ", e);
                    e.printStackTrace();
                }
            }
        }

        void tearDown() {
            isRunning = false;
            try {
                Log.d(TAG, "Attempting to close server socket.");
                serverSocket.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Error closing ServerSocket: ", e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Connection between {@link ServerService} and it's clients
     */
    private static class Connection {

        Connection(Socket socket) {
            Log.d(TAG, "Connected to new client");

            if (socket != null && socket.isConnected()) {
                CommsProtocol commsProtocol = new ProxyProtocol();

                try {

                    PrintWriter out = createPrintWriter(socket);
                    BufferedReader in = createBufferedReader(socket);

                    String inputLine, outputLine;

                    // Initiate conversation with client
                    outputLine = commsProtocol.processInput(null).serialize();

                    out.println(outputLine);

                    while ((inputLine = in.readLine()) != null) {
                        outputLine = commsProtocol.processInput(inputLine).serialize();
                        out.println(outputLine);

                        Log.d(TAG, "Read from client stream: " + inputLine);

                        if (outputLine.equals("Bye.")) break;
                    }

                    // Close protocol
                    commsProtocol.close();
                    in.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    // Try to close protocol if disconnected
                    try {
                        commsProtocol.close();
                    }
                    catch (IOException e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }

    }
}
