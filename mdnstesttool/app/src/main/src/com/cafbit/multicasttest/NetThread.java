/*
 * Copyright 2011 David Simmons
 * http://cafbit.com/entry/testing_multicast_support_on_android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cafbit.multicasttest;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import com.cafbit.multicasttest.MulticastTestActivity;
import com.cafbit.multicasttest.Packet;
import com.cafbit.multicasttest.Util;
import com.cafbit.netlib.NetUtil;
import com.cafbit.netlib.dns.DNSMessage;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This thread runs in the background while the user has our
 * program in the foreground, and handles sending mDNS queries
 * and processing incoming mDNS packets.
 * @author simmons
 */
public class NetThread extends Thread {

	public static final String TAG = "NetThread";

	// the standard mDNS multicast address and port number
	private static final byte[] MDNS_ADDR =
			new byte[] {(byte) 224,(byte) 0,(byte) 0,(byte) 251};
	private static final int MDNS_PORT = 5353;


	private NetworkInterface 		networkInterface;
	private InetAddress 			groupAddress;
	private MulticastSocket 		multicastSocket;
	private NetUtil 				netUtil;
	private MulticastTestActivity 	activity;
	private ReceivedTask			mReceivedTask;
	private Set<InetAddress> 		localAddresses;
	private MulticastLock 			multicastLock;

	private JSONObject 				json;
	private DNSMessage              message;
	/**
	 * Construct the network thread.
	 * @param activity
	 */
	public NetThread(MulticastTestActivity activity) {
		super("net");
		this.activity = activity;
		netUtil = new NetUtil(activity);
	}

	/**
	 * Open a multicast socket on the mDNS address and port.
	 * @throws IOException
	 */
	private void openSocket() throws IOException {
		Log.i(TAG,"OpenScoket");
		multicastSocket = new MulticastSocket(MDNS_PORT);
		multicastSocket.setTimeToLive(255);
		multicastSocket.setReuseAddress(true);
		multicastSocket.setNetworkInterface(networkInterface);
		multicastSocket.joinGroup(groupAddress);
	}

	/**
	 * The main network loop.  Multicast DNS packets are received,
	 * processed, and sent to the UI.
	 * 
	 * This loop may be interrupted by closing the multicastSocket,
	 * at which time any commands in the commandQueue will be
	 * processed.
	 */
	@Override
	public void run() {
		Log.v(TAG, "starting network thread");

		localAddresses 	= NetUtil.getLocalAddresses();
		multicastLock 	= null;

		// initialize the network
		try {
			networkInterface = netUtil.getFirstWifiOrEthernetInterface();
			if (networkInterface == null) {
				throw new IOException("Your WiFi is not enabled.");
			}
			groupAddress = InetAddress.getByAddress(MDNS_ADDR); 

			WifiManager wm = (WifiManager)MulticastTestActivity.mThis.getSystemService(MulticastTestActivity.mThis.getApplicationContext().WIFI_SERVICE);
			WifiManager.MulticastLock multicastLock = wm.createMulticastLock("mydebuginfo");
			multicastLock.acquire();

			openSocket();
		} catch (IOException e1) {
			Log.i(TAG,"IOException : " + e1.getMessage());
			return;
		}

		
		mReceivedTask = new ReceivedTask();
		mReceivedTask.execute("");
		try {
			mReceivedTask.get();
		} catch (InterruptedException e1) {

		} catch (ExecutionException e1) {
		}

	}

	/**
	 * Transmit an mDNS query on the local network.
	 * @param host
	 * @throws IOException
	 */
	private void query(String host) throws IOException {
		Log.i(TAG,"query  essai 1: " + host);
		byte[] requestData = (new DNSMessage(host)).serialize();
		DatagramPacket request =
				new DatagramPacket(requestData, requestData.length, InetAddress.getByAddress(MDNS_ADDR), MDNS_PORT);
		multicastSocket.send(request);
	}

	public void submitQuery(String host) {
		Log.i(TAG,"submitQuery : " + host);
		try {
			query(host);
		} catch (IOException e) {
			Log.i(TAG,"Excepton from send : " + e.getMessage());
		}
	}
	public void submitQuit() {

		multicastSocket.close();
		mReceivedTask.cancel(true);
		if (multicastLock != null) {
		    multicastLock.release();
		    multicastLock = null;
		}
	}

	private class ReceivedTask extends AsyncTask<String, Integer, String> {
		protected String doInBackground(String... params) {
			while (!isCancelled()) 
			{
				// receive a packet (or process an incoming command)
				try {
					byte [] buffer = new byte[512]; 
				    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
					
					multicastSocket.receive(response);

					Log.i(TAG," Received MSG with length : " + response.getLength());
					
					try {
						message = new DNSMessage(response.getData(), response.getOffset(), response.getLength());
					} catch (Exception e) {
						Log.i(TAG,"Exception new DNSMessage: " + e.getMessage());
						continue;
					}

		
					//json = message.toJSON();


					if (localAddresses.contains(response.getAddress())) {
						Log.i(TAG,"Ignore our loacal adress" );
						continue;
					}

					if(message.toString().trim().contains("_sdtvwcam"))
					{
						Log.i(TAG,"ReceivedTask Message : " + message.toString().trim());
						// send the packet to the UI
					}
					else
					{
						Log.i(TAG,"ReceivedTask Message : " + message.toString().trim());
						
					}					
				} catch (IOException e) {
					Log.i(TAG,"IOException from received : " + e.getMessage());
				}

			}  
			return "Executed";
		}
		protected void onCancelled(){
		}

	}

}
