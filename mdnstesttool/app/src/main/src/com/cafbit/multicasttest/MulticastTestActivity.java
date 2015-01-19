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

import com.cafbit.netlib.dns.DNSMessage;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * This utility listens for mDNS multicast activity on the local network,
 * and allows the user to submit their own mDNS multicast queries.
 * 
 * @author simmons
 */
public class MulticastTestActivity extends Activity {

	public static final String TAG = "MulticastTest";
	public static 	MulticastTestActivity 		mThis;

	private TextView statusLine;
	private EditText hostBox;
	private Button  btQuery;
	private Button  btClear;
	private NetThread netThread = null;
	private LaunchTask mlaunchTask;
	private PacketListAdapter packetListAdapter;
	private MulticastLock 			multicastLock;

	/**
	 * Set up the user interface and perform certain setup steps.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		hostBox = (EditText) findViewById(R.id.host_box);
		hostBox.setText("_sdtvwcam._tcp.local.");
		mThis = this;
		
		WifiManager wm = (WifiManager)MulticastTestActivity.mThis.getSystemService(MulticastTestActivity.mThis.getApplicationContext().WIFI_SERVICE);
		WifiManager.MulticastLock multicastLock = wm.createMulticastLock("mydebuginfo");
		multicastLock.acquire();
		
		netThread = new NetThread(this);
		
		mlaunchTask = new LaunchTask();
		mlaunchTask.execute("");
		
	
		
		btClear = (Button)findViewById(R.id.ClearButton);
		btClear.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				packetListAdapter.clear();
			}
		});

		btQuery = (Button)findViewById(R.id.QueryButton);
		btQuery.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Log.i("ZLO","handleQueryButton");
				String host = hostBox.getText().toString().trim();
				if (host.length() == 0) {
					return;
				}

				
				try {
					Log.i("ZLO","Before handleQueryButton");
					netThread.submitQuery(host);
					Log.i("ZLO","handleQueryButton");
				} catch (Exception e) {
					Log.w(TAG, e.getMessage(), e);
				
					return;
				}
			
			}
		});

	}
	private class LaunchTask extends AsyncTask<String, Integer, String> {
		protected String doInBackground(String... params) {
			
			netThread.run();
			return "Executed";
		}
		protected void onCancelled(){
		}

	}

}