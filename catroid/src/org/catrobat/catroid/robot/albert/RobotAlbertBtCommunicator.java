/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *    
 *    This file incorporates work covered by the following copyright and  
 *    permission notice: 
 *    
 *		   	Copyright 2010 Guenther Hoelzl, Shawn Brown
 *
 *		   	This file is part of MINDdroid.
 *
 * 		  	MINDdroid is free software: you can redistribute it and/or modify
 * 		  	it under the terms of the GNU Affero General Public License as
 * 		  	published by the Free Software Foundation, either version 3 of the
 *   		License, or (at your option) any later version.
 *
 *   		MINDdroid is distributed in the hope that it will be useful,
 *   		but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   		MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   		GNU Affero General Public License for more details.
 *
 *   		You should have received a copy of the GNU Affero General Public License
 *   		along with MINDdroid.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.robot.albert;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;

import org.catrobat.catroid.R;
import org.catrobat.catroid.bluetooth.BTConnectable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

//This code is based on the nxt-implementation
public class RobotAlbertBtCommunicator extends RobotAlbertCommunicator {

	private static final UUID SERIAL_PORT_SERVICE_CLASS_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fc");

	private BluetoothAdapter btAdapter;
	private BluetoothSocket btSocket = null;
	private OutputStream outputStream = null;
	private InputStream inputStream = null;

	private String mMacAddress;
	private BTConnectable myOwner;
	private static boolean debugOutput = false;

	public RobotAlbertBtCommunicator(BTConnectable myOwner, Handler uiHandler, BluetoothAdapter btAdapter,
			Resources resources) {
		super(uiHandler, resources);

		this.myOwner = myOwner;
		this.btAdapter = btAdapter;
	}

	public void setMACAddress(String mMACaddress) {
		this.mMacAddress = mMACaddress;
	}

	@Override
	public void run() {

		try {
			createConnection();
		} catch (IOException e) {
		}

		while (connected) {
			try {
				receiveMessage();
			} catch (IOException e) {
				Log.d("RobotAlbertBtComm", "IOException in run:receiveMessage occured: " + e.toString());
				if (connected == true) {
					sendState(STATE_CONNECTERROR);
					connected = false;
				}
			} catch (Exception e) {
				Log.d("RobotAlbertBtComm", "Exception in run:receiveMessage occured: " + e.toString());
				if (connected == true) {
					sendState(STATE_CONNECTERROR);
					connected = false;
				}
			}
		}
	}

	@Override
	public void createConnection() throws IOException {
		try {
			BluetoothSocket btSocketTemporary;
			BluetoothDevice btDevice = null;
			btDevice = btAdapter.getRemoteDevice(mMacAddress);
			if (btDevice == null) {
				if (uiHandler == null) {
					throw new IOException();
				} else {
					sendToast(resources.getString(R.string.no_paired_nxt));
					sendState(STATE_CONNECTERROR);
					return;
				}
			}

			btSocketTemporary = btDevice.createRfcommSocketToServiceRecord(SERIAL_PORT_SERVICE_CLASS_UUID);
			try {
				btSocketTemporary.connect();

			} catch (IOException e) {
				if (myOwner.isPairing()) {
					if (uiHandler != null) {
						sendToast(resources.getString(R.string.pairing_message));
						sendState(STATE_CONNECTERROR_PAIRING);
					} else {
						throw e;
					}
					return;
				}

				//try another method for connection, this should work on the HTC desire, credits to Michael Biermann
				try {

					Method mMethod = btDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
					btSocketTemporary = (BluetoothSocket) mMethod.invoke(btDevice, Integer.valueOf(1));
					btSocketTemporary.connect();
				} catch (Exception e1) {
					if (uiHandler == null) {
						throw new IOException();
					} else {
						sendState(STATE_CONNECTERROR);
					}
					return;
				}
			}
			btSocket = btSocketTemporary;
			inputStream = btSocket.getInputStream();
			outputStream = btSocket.getOutputStream();
			connected = true;
		} catch (IOException e) {
			if (uiHandler == null) {
				throw e;
			} else {
				if (myOwner.isPairing()) {
					sendToast(resources.getString(R.string.pairing_message));
				}
				sendState(STATE_CONNECTERROR);
				return;
			}
		}
		// everything was OK
		if (uiHandler != null) {
			sendState(STATE_CONNECTED);
		}
	}

	@Override
	public void destroyConnection() throws IOException {

		Log.d("RobotAlbertBtComm", "destroyRobotAlbertConnection");

		if (connected) {
			stopAllMovement();
		}

		try {
			if (btSocket != null) {
				connected = false;
				btSocket.close();
				btSocket = null;
			}

			inputStream = null;
			outputStream = null;

		} catch (IOException e) {
			if (uiHandler == null) {
				throw e;
			} else {
				sendToast(resources.getString(R.string.problem_at_closing));
			}
		}
	}

	@Override
	public void stopAllMovement() {
		myHandler.removeMessages(0);
		myHandler.removeMessages(1);
		myHandler.removeMessages(2);
		resetRobotAlbert();
	}

	/**
	 * Sends a message on the opened OutputStream
	 * 
	 * @param message
	 *            , the message as a byte array
	 */
	@Override
	public void sendMessage(byte[] message) throws IOException {

		try {
			if (outputStream == null) {
				throw new IOException();
			}
			outputStream.write(message, 0, message.length);
			outputStream.flush();
		} catch (Exception e) {
			Log.d("RobotAlbertBtComm", "ERROR: Exception occured in sendMessage " + e.getMessage());
		}
	}

	/**
	 * Receives a message on the opened InputStream
	 * 
	 * @return the message
	 */
	@Override
	public byte[] receiveMessage() throws IOException, Exception {

		if (inputStream == null) {
			throw new IOException(" Software caused connection abort ");
		}

		byte[] buffer = new byte[100];
		@SuppressWarnings("unused")
		int read = 0;
		byte[] buf = new byte[1];
		byte[] buf0 = new byte[2];
		int count2 = 0;

		do {
			checkIfDataIsAvailable();
			read = inputStream.read(buf0);
			count2++;
			if (count2 > 200) {
				return null;
			}
		} while ((buf0[0] != -86) || (buf0[1] != 85));

		int count = 2;
		buffer[0] = buf0[0];
		buffer[1] = buf0[1];

		do {
			checkIfDataIsAvailable();
			read = inputStream.read(buf);
			buffer[count] = buf[0];
			count++;
		} while ((buffer[count] != 13) && (buffer[count - 1] != 10));

		int leftDistance = (buffer[14] + buffer[16] + buffer[18] + buffer[20]) / 4;
		int rightDistance = (buffer[13] + buffer[15] + buffer[17] + buffer[19]) / 4;

		sensors.setValueOfLeftDistanceSensor(leftDistance);
		sensors.setValueOfRightDistanceSensor(rightDistance);

		if (debugOutput == true) {
			Log.d("RobotAlbertBtComm", "sensor packet found");
			Log.d("RobotAlbertBtComm", "receiveMessage:  leftDistance=" + leftDistance);
			Log.d("RobotAlbertBtComm", "receiveMessage: rightDistance=" + rightDistance);
		}
		return buffer;
	}

	public void checkIfDataIsAvailable() throws IOException {
		int available = 0;
		long timeStart = System.currentTimeMillis();
		long timePast;

		while (true) {
			if (inputStream == null) {
				throw new IOException(" Software caused connection abort ");
			}
			available = inputStream.available();
			if (available > 0) {
				break;
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// here you can optionally check elapsed time, and time out
			timePast = System.currentTimeMillis();
			if ((timePast - timeStart) > 13000) {
				Log.d("AlbertRobot-Timeout", "TIMEOUT for receive message occured");
				throw new IOException(" Software caused connection abort because of timeout");
			}
		}
	}

}
